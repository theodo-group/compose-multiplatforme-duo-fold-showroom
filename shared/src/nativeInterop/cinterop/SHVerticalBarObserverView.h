#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * A transparent view that fills its superview and takes no touches. It reports where the system
 * places the iPhone Duo vertical bar (status bar + toolbar) for the window it lives in, read from
 * `UITraitCollection.verticalBarEdge` (iOS 27.1). On older systems the edge stays unspecified.
 */
@interface SHVerticalBarObserverView : UIView
- (instancetype)initWithChangeHandler:(void (^)(void))changeHandler;

/** Whether the system has the vertical bar trait at all (iOS 27.1+). */
@property (nonatomic, readonly) BOOL verticalBarSupported;
/**
 * The physical edge of the vertical bar, already resolved from leading/trailing with this view's
 * layout direction: 0 none (horizontal bars), 1 left, 2 right.
 */
@property (nonatomic, readonly) NSInteger barEdge;
/** Raw UIVerticalBarEdge: unspecified 0, leading 1, trailing 2. */
@property (nonatomic, readonly) NSInteger rawVerticalBarEdge;
/**
 * Where app controls start in the vertical bar, from the top of this view, in points: the top inset
 * of the system bar layout region on that edge (`UIViewLayoutRegion` bar region), i.e. just below
 * the vertical status bar. 0 without a vertical bar.
 */
@property (nonatomic, readonly) double barItemsTop;

/** Stops observing, drops the change handler and leaves the superview. */
- (void)detach;
@end

NS_ASSUME_NONNULL_END
