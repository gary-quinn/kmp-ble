#import <CoreBluetooth/CoreBluetooth.h>

typedef void (^KmpBleConnectionFailureBlock)(CBPeripheral * _Nonnull, NSError * _Nullable);

/**
 * ObjC proxy that wraps a Kotlin-implemented CBCentralManagerDelegate and adds
 * didFailToConnectPeripheral: support. K/N cannot override this method because
 * its type signature collides with didDisconnectPeripheral:error: in Kotlin.
 *
 * All delegate methods except didFailToConnectPeripheral are forwarded to the
 * wrapped target via ObjC message forwarding.
 */
@interface KmpBleDelegateProxy : NSObject <CBCentralManagerDelegate>

@property (nonatomic, strong, nonnull) id<CBCentralManagerDelegate> target;
@property (nonatomic, copy, nullable) KmpBleConnectionFailureBlock onConnectionFailure;

- (nonnull instancetype)initWithTarget:(nonnull id<CBCentralManagerDelegate>)target;

@end
