#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * A transparent view that fills its superview and takes no touches. It owns the iPhone Duo hinge
 * interaction (iOS 27.1) and reads the fold "division" reserved region during layout.
 *
 * Approach adapted from the Crease library (https://github.com/jantursky/crease, MIT licence).
 */
@interface SHHingeObserverView : UIView
- (instancetype)initWithChangeHandler:(void (^)(void))changeHandler;

/** Whether the system has the hinge interaction API at all (iOS 27.1+). */
@property (nonatomic, readonly) BOOL hingeSupported;
/** Whether an update has reported a hinge on this device. */
@property (nonatomic, readonly) BOOL hasHinge;
/** Raw UIHingeStatus: unknown 0, closed 1, partially open 2, fully open 3. */
@property (nonatomic, readonly) NSInteger hingeStatus;
/** Hinge angle in radians: 0 shut, pi flat. */
@property (nonatomic, readonly) double hingeAngle;

/** Whether the system reports a fold division region for this view. */
@property (nonatomic, readonly) BOOL hasDivision;
/** Whether that division currently separates content (device partially open). */
@property (nonatomic, readonly) BOOL divisionActive;
/**
 * The division in this view's coordinates, in points: the region frame minus its margins, i.e. the
 * fold itself. Can have zero width or height on a seamless fold.
 */
@property (nonatomic, readonly) double divisionX;
@property (nonatomic, readonly) double divisionY;
@property (nonatomic, readonly) double divisionWidth;
@property (nonatomic, readonly) double divisionHeight;
/** Margins the system adds around the division for interactive content, in points. */
@property (nonatomic, readonly) double divisionMarginTop;
@property (nonatomic, readonly) double divisionMarginLeft;
@property (nonatomic, readonly) double divisionMarginBottom;
@property (nonatomic, readonly) double divisionMarginRight;

/** Stops observing, drops the change handler and leaves the superview. */
- (void)detach;
@end

NS_ASSUME_NONNULL_END
