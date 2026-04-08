#import "KmpBleDelegateProxy.h"

@implementation KmpBleDelegateProxy

- (nonnull instancetype)initWithTarget:(nonnull id<CBCentralManagerDelegate>)target {
    self = [super init];
    if (self) {
        _target = target;
    }
    return self;
}

- (void)centralManager:(nonnull CBCentralManager *)central
    didFailToConnectPeripheral:(nonnull CBPeripheral *)peripheral
                         error:(nullable NSError *)error {
    if (self.onConnectionFailure) {
        self.onConnectionFailure(peripheral, error);
    }
}

- (BOOL)respondsToSelector:(SEL)aSelector {
    return [super respondsToSelector:aSelector] || [self.target respondsToSelector:aSelector];
}

- (id)forwardingTargetForSelector:(SEL)aSelector {
    if ([self.target respondsToSelector:aSelector]) {
        return self.target;
    }
    return [super forwardingTargetForSelector:aSelector];
}

@end
