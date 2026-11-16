#import <dispatch/dispatch.h>
#import "SHHingeObserverView.h"

/*
 * Reads the iPhone Duo hinge and the fold division (iOS 27.1) through the Objective-C runtime.
 *
 * No iOS 27.1 type is named here: classes are looked up by name, selectors are declared in private
 * protocols, and every send is guarded by a responds check. With an older SDK this still compiles,
 * and on an older system the view stays inert (no hinge reported, no region read).
 *
 * Approach adapted from the Crease library (https://github.com/jantursky/crease, MIT licence).
 * Once the project requires the iOS 27.1 SDK, this file can be replaced by direct calls to
 * UIHingeInteraction and -[UIView reservedRegionsOfKind:options:].
 */

@protocol SHHingeInteractionInit
- (instancetype)initWithUpdateHandler:(void (^)(id interaction, id update))updateHandler;
@end

@protocol SHHingeUpdate
- (nullable id)hinge;
@end

@protocol SHHinge
- (NSInteger)status;
- (CGFloat)angle;
@end

@protocol SHReservedRegionKinds
- (id)divisionRegionKind;
@end

@protocol SHReservedRegionsView
- (NSArray *)reservedRegionsOfKind:(id)kind options:(NSUInteger)options;
@end

@protocol SHReservedRegion
- (CGRect)frame;
- (UIEdgeInsets)margins;
- (BOOL)isActive;
@end

static const NSUInteger SHIncludeInactive = 1;
static const NSInteger SHStatusPartiallyOpen = 2;
static const int SHMaxRegionRetries = 10;
static const int64_t SHRegionRetryDelay = 100 * NSEC_PER_MSEC;

@interface SHHingeObserverView ()
@property (nonatomic, copy, nullable) void (^changeHandler)(void);
@property (nonatomic, strong, nullable) id interaction;
@property (nonatomic) NSUInteger retryGeneration;
@end

@implementation SHHingeObserverView

- (instancetype)initWithChangeHandler:(void (^)(void))changeHandler {
    if ((self = [super initWithFrame:CGRectZero])) {
        _changeHandler = [changeHandler copy];
        self.userInteractionEnabled = NO;
        self.backgroundColor = UIColor.clearColor;
        self.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
        [self installInteraction];
    }
    return self;
}

- (BOOL)hingeSupported {
    return _interaction != nil;
}

- (void)installInteraction {
    Class cls = NSClassFromString(@"UIHingeInteraction");
    if (cls == Nil || ![cls instancesRespondToSelector:@selector(initWithUpdateHandler:)]) return;
    __weak SHHingeObserverView *weakSelf = self;
    id interaction = [(id<SHHingeInteractionInit>)[cls alloc] initWithUpdateHandler:^(id interaction, id update) {
        [weakSelf handleUpdate:update];
    }];
    // addInteraction: raises for an object that is not a UIInteraction.
    if (![interaction conformsToProtocol:@protocol(UIInteraction)]) return;
    [self addInteraction:(id<UIInteraction>)interaction];
    _interaction = interaction;
}

- (void)detach {
    if (_interaction != nil) {
        [self removeInteraction:(id<UIInteraction>)_interaction];
        _interaction = nil;
    }
    _retryGeneration++;
    // The handler reaches back into Kotlin; dropping it breaks the cycle through the view.
    _changeHandler = nil;
    [self removeFromSuperview];
}

- (void)handleUpdate:(id)update {
    id hinge = [update respondsToSelector:@selector(hinge)] ? [(id<SHHingeUpdate>)update hinge] : nil;
    if (hinge != nil && [hinge respondsToSelector:@selector(status)] && [hinge respondsToSelector:@selector(angle)]) {
        _hasHinge = YES;
        _hingeStatus = [(id<SHHinge>)hinge status];
        _hingeAngle = [(id<SHHinge>)hinge angle];
    } else {
        _hasHinge = NO;
        _hingeStatus = 0;
        _hingeAngle = 0;
    }
    [self readDivision];
    [self notifyChange];
    // The division can lag the hinge and no layout pass follows on its own, so it is read again
    // until it agrees with the hinge.
    [self retryDivisionWithGeneration:++_retryGeneration remaining:SHMaxRegionRetries];
}

- (void)retryDivisionWithGeneration:(NSUInteger)generation remaining:(int)remaining {
    BOOL folded = _hasHinge && _hingeStatus == SHStatusPartiallyOpen;
    if (remaining <= 0 || (_hasDivision && _divisionActive == folded)) return;
    __weak SHHingeObserverView *weakSelf = self;
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, SHRegionRetryDelay), dispatch_get_main_queue(), ^{
        SHHingeObserverView *strongSelf = weakSelf;
        if (strongSelf == nil || strongSelf.retryGeneration != generation) return;
        [strongSelf readDivision];
        [strongSelf notifyChange];
        [strongSelf retryDivisionWithGeneration:generation remaining:remaining - 1];
    });
}

- (void)layoutSubviews {
    [super layoutSubviews];
    [self readDivision];
    [self notifyChange];
}

- (void)readDivision {
    _hasDivision = NO;
    _divisionActive = NO;
    _divisionX = _divisionY = _divisionWidth = _divisionHeight = 0;
    _divisionMarginTop = _divisionMarginLeft = _divisionMarginBottom = _divisionMarginRight = 0;

    id kinds = NSClassFromString(@"UIViewReservedRegionKind");
    if (kinds == nil || ![kinds respondsToSelector:@selector(divisionRegionKind)]) return;
    id kind = [(id<SHReservedRegionKinds>)kinds divisionRegionKind];
    if (kind == nil || ![self respondsToSelector:@selector(reservedRegionsOfKind:options:)]) return;
    NSArray *regions = [(id<SHReservedRegionsView>)self reservedRegionsOfKind:kind options:SHIncludeInactive];
    if (![regions isKindOfClass:[NSArray class]]) return;

    for (id region in regions) {
        if (![region respondsToSelector:@selector(frame)]) continue;
        CGRect frame = [(id<SHReservedRegion>)region frame];
        // Per UIViewReservedRegion.h, `frame` includes margins kept clear of interactive content
        // around the reserved rect. The fold itself is the frame minus those margins (often a
        // zero-thickness line on a seamless fold, like Android's FoldingFeature bounds).
        if ([region respondsToSelector:@selector(margins)]) {
            UIEdgeInsets margins = [(id<SHReservedRegion>)region margins];
            _divisionMarginTop = margins.top;
            _divisionMarginLeft = margins.left;
            _divisionMarginBottom = margins.bottom;
            _divisionMarginRight = margins.right;
            CGRect inner = CGRectMake(frame.origin.x + margins.left,
                                      frame.origin.y + margins.top,
                                      MAX(0, frame.size.width - margins.left - margins.right),
                                      MAX(0, frame.size.height - margins.top - margins.bottom));
            frame = inner;
        }
        _hasDivision = YES;
        _divisionActive = [region respondsToSelector:@selector(isActive)]
            ? [(id<SHReservedRegion>)region isActive]
            : YES;
        _divisionX = frame.origin.x;
        _divisionY = frame.origin.y;
        _divisionWidth = frame.size.width;
        _divisionHeight = frame.size.height;
        return;
    }
}

- (void)notifyChange {
    if (_changeHandler != nil) _changeHandler();
}

@end
