#import "SHVerticalBarObserverView.h"

/*
 * Reads the iPhone Duo vertical bar edge (iOS 27.1) through the Objective-C runtime, like the hinge
 * bridge: no iOS 27.1 symbol is named, so this compiles with an older SDK and stays inert (edge 0)
 * on an older system.
 *
 * `verticalBarEdge` is a trait: the system resolves it per window from the display (outer, or inner
 * in landscape), the orientation and the window's place on screen (each Split View window gets its
 * own outer edge). It is re-read on every trait change that affects it and on every layout pass,
 * which covers rotation, fold / unfold and Split View resizing.
 */

@protocol SHVerticalBarTraits
- (NSInteger)verticalBarEdge;
@end

// Class method of UITraitCollection, sent to the class object.
@protocol SHVerticalBarTraitsClass
- (NSArray *)systemTraitsAffectingVerticalBarEdge;
@end

@protocol SHBarLayoutRegionClass
- (id)layoutRegionForBarOnEdge:(UIRectEdge)edge;
- (id)layoutRegionForBarOnEdge:(UIRectEdge)edge extent:(CGFloat)extent;
@end

@protocol SHLayoutRegionView
- (UIEdgeInsets)edgeInsetsForLayoutRegion:(id)layoutRegion;
@end

/** Bar extent for the iOS 27.1 region API, which has no default extent. */
static const CGFloat SHFallbackBarExtent = 48;
static const NSInteger SHRawLeading = 1;
static const NSInteger SHRawTrailing = 2;
static const NSInteger SHEdgeLeft = 1;
static const NSInteger SHEdgeRight = 2;

@interface SHVerticalBarObserverView ()
@property (nonatomic, copy, nullable) void (^changeHandler)(void);
@property (nonatomic, strong, nullable) id registration;
@end

@implementation SHVerticalBarObserverView

- (instancetype)initWithChangeHandler:(void (^)(void))changeHandler {
    if ((self = [super initWithFrame:CGRectZero])) {
        _changeHandler = [changeHandler copy];
        self.userInteractionEnabled = NO;
        self.backgroundColor = UIColor.clearColor;
        self.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
        _verticalBarSupported = [UITraitCollection instancesRespondToSelector:@selector(verticalBarEdge)];
        [self registerForBarTraits];
    }
    return self;
}

- (void)registerForBarTraits {
    if (!_verticalBarSupported) return;
    id cls = [UITraitCollection class];
    if (![cls respondsToSelector:@selector(systemTraitsAffectingVerticalBarEdge)]) return;
    NSArray *traits = [(id<SHVerticalBarTraitsClass>)cls systemTraitsAffectingVerticalBarEdge];
    if (![traits isKindOfClass:[NSArray class]] || traits.count == 0) return;
    __weak SHVerticalBarObserverView *weakSelf = self;
    _registration = [self registerForTraitChanges:traits
                                      withHandler:^(id<UITraitEnvironment> env, UITraitCollection *previous) {
        [weakSelf refresh];
    }];
}

- (void)detach {
    if (_registration != nil) {
        [self unregisterForTraitChanges:_registration];
        _registration = nil;
    }
    // The handler reaches back into Kotlin; dropping it breaks the cycle through the view.
    _changeHandler = nil;
    [self removeFromSuperview];
}

- (void)didMoveToWindow {
    [super didMoveToWindow];
    [self refresh];
}

- (void)safeAreaInsetsDidChange {
    [super safeAreaInsetsDidChange];
    [self refresh];
}

- (void)layoutSubviews {
    [super layoutSubviews];
    [self refresh];
}

- (void)refresh {
    NSInteger raw = 0;
    UITraitCollection *traits = self.traitCollection;
    if (_verticalBarSupported && [traits respondsToSelector:@selector(verticalBarEdge)]) {
        raw = [(id<SHVerticalBarTraits>)traits verticalBarEdge];
    }
    // Leading / trailing follow the layout direction; the bar itself stays on its hardware side.
    BOOL rtl = self.effectiveUserInterfaceLayoutDirection == UIUserInterfaceLayoutDirectionRightToLeft;
    NSInteger edge = 0;
    if (raw == SHRawLeading) edge = rtl ? SHEdgeRight : SHEdgeLeft;
    else if (raw == SHRawTrailing) edge = rtl ? SHEdgeLeft : SHEdgeRight;

    // The bar layout region is where app controls go in the vertical bar: it starts below the
    // status bar (UIStatusBarManager.statusBarFrame does not describe the vertical status bar).
    double itemsTop = 0;
    if (edge != 0) {
        UIEdgeInsets insets = [self barRegionInsetsOnEdge:edge == SHEdgeLeft ? UIRectEdgeLeft : UIRectEdgeRight];
        itemsTop = insets.top;
    }

    BOOL changed = raw != _rawVerticalBarEdge || edge != _barEdge || itemsTop != _barItemsTop;
    _rawVerticalBarEdge = raw;
    _barEdge = edge;
    _barItemsTop = itemsTop;
    if (changed && _changeHandler != nil) _changeHandler();
}

- (UIEdgeInsets)barRegionInsetsOnEdge:(UIRectEdge)edge {
    id cls = NSClassFromString(@"UIViewLayoutRegion");
    if (cls == nil || ![self respondsToSelector:@selector(edgeInsetsForLayoutRegion:)]) return UIEdgeInsetsZero;
    id region = nil;
    if ([cls respondsToSelector:@selector(layoutRegionForBarOnEdge:)]) {
        region = [(id<SHBarLayoutRegionClass>)cls layoutRegionForBarOnEdge:edge];
    } else if ([cls respondsToSelector:@selector(layoutRegionForBarOnEdge:extent:)]) {
        region = [(id<SHBarLayoutRegionClass>)cls layoutRegionForBarOnEdge:edge extent:SHFallbackBarExtent];
    }
    if (region == nil) return UIEdgeInsetsZero;
    return [(id<SHLayoutRegionView>)self edgeInsetsForLayoutRegion:region];
}

@end
