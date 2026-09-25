// JNI bridge between the kmp-ble macOS backend and CoreBluetooth.
//
// Every CoreBluetooth call runs on one serial dispatch queue, which is also the delegate
// queue, so no main run loop is needed. Callbacks reach the JVM through a single
// NativeCallback.onEvent(kind, a, b, status, text, data) with plain types only; CoreBluetooth
// objects cross the boundary as opaque long handles. Event kinds must match MacosEventKind.

#import <CoreBluetooth/CoreBluetooth.h>
#import <Foundation/Foundation.h>
#include <dlfcn.h>
#include <jni.h>
#include <libproc.h>
#include <pthread.h>
#include <unistd.h>

enum {
    KB_CENTRAL_STATE = 1,
    KB_DISCOVERED = 2,
    KB_CONNECTED = 3,
    KB_CONNECT_FAILED = 4,
    KB_DISCONNECTED = 5,
    KB_SERVICES_DISCOVERED = 6,
    KB_CHARACTERISTICS_DISCOVERED = 7,
    KB_DESCRIPTORS_DISCOVERED = 8,
    KB_VALUE_UPDATED = 9,
    KB_VALUE_WRITTEN = 10,
    KB_DESCRIPTOR_VALUE = 11,
    KB_DESCRIPTOR_WRITTEN = 12,
    KB_NOTIFY_STATE = 13,
    KB_RSSI = 14,
    KB_READY_TO_WRITE = 15,
    KB_SERVICES_MODIFIED = 16,
    KB_L2CAP_OPENED = 17,
    KB_L2CAP_DATA = 18,
    KB_L2CAP_CLOSED = 19,
    KB_PM_STATE = 20,
    KB_PM_SERVICE_ADDED = 21,
    KB_PM_ADVERTISING_STARTED = 22,
    KB_PM_READ_REQUEST = 23,
    KB_PM_WRITE_REQUESTS = 24,
    KB_PM_SUBSCRIBED = 25,
    KB_PM_UNSUBSCRIBED = 26,
    KB_PM_READY_TO_UPDATE = 27,
    KB_PM_L2CAP_PUBLISHED = 28,
    KB_PM_L2CAP_OPENED = 29,
};

static const int KB_API_VERSION = 2;
static NSString *const kUsageDescriptionKey = @"NSBluetoothAlwaysUsageDescription";
#define KB_L2CAP_READ_BUFFER 4096
#define KB_PRUNE_EVERY 512
#define KB_PRUNE_AGE_SECONDS 300

static JavaVM *gJvm;
static jobject gCallback;
static jmethodID gOnEvent;
static dispatch_queue_t gQueue;
static void *const kQueueKey = (void *)&kQueueKey;

static NSMutableDictionary<NSNumber *, id> *gObjects;
static NSMapTable<id, NSNumber *> *gHandles;
static int64_t gNextHandle = 1;
static NSMutableDictionary<NSString *, CBPeripheral *> *gPeripheralsById;
static NSMutableDictionary<NSString *, CBCentral *> *gCentralsById;
static NSMutableDictionary<NSString *, NSDate *> *gLastSeen;
static NSMutableSet<NSString *> *gRetainedIds;
static NSUInteger gDiscoveriesSincePrune;
static pthread_key_t gDetachKey;

@class KBCentralDelegate;
@class KBPeripheralManagerDelegate;
static CBCentralManager *gCentral;
static KBCentralDelegate *gCentralDelegate;
static CBPeripheralManager *gPeripheralManager;
static KBPeripheralManagerDelegate *gPeripheralManagerDelegate;
static NSThread *gStreamThread;

#pragma mark - JNI plumbing

static void kbDetachThread(void *unused) {
    if (gJvm != NULL) (*gJvm)->DetachCurrentThread(gJvm);
}

static JNIEnv *kbEnv(void) {
    JNIEnv *env = NULL;
    jint rc = (*gJvm)->GetEnv(gJvm, (void **)&env, JNI_VERSION_1_8);
    if (rc == JNI_EDETACHED) {
        if ((*gJvm)->AttachCurrentThreadAsDaemon(gJvm, (void **)&env, NULL) != JNI_OK) return NULL;
        pthread_setspecific(gDetachKey, (void *)1);
    } else if (rc != JNI_OK) {
        return NULL;
    }
    return env;
}

static jstring kbJString(JNIEnv *env, NSString *value) {
    if (value == nil) return NULL;
    NSUInteger length = value.length;
    unichar *chars = malloc(sizeof(unichar) * (length > 0 ? length : 1));
    [value getCharacters:chars range:NSMakeRange(0, length)];
    jstring result = (*env)->NewString(env, chars, (jsize)length);
    free(chars);
    return result;
}

static NSString *kbNSString(JNIEnv *env, jstring value) {
    if (value == NULL) return nil;
    const jchar *chars = (*env)->GetStringChars(env, value, NULL);
    jsize length = (*env)->GetStringLength(env, value);
    NSString *result = [NSString stringWithCharacters:chars length:(NSUInteger)length];
    (*env)->ReleaseStringChars(env, value, chars);
    return result;
}

static NSData *kbNSData(JNIEnv *env, jbyteArray value) {
    if (value == NULL) return nil;
    jsize length = (*env)->GetArrayLength(env, value);
    NSMutableData *data = [NSMutableData dataWithLength:(NSUInteger)length];
    if (length > 0) (*env)->GetByteArrayRegion(env, value, 0, length, data.mutableBytes);
    return data;
}

static NSArray<NSString *> *kbStringArray(JNIEnv *env, jobjectArray value) {
    if (value == NULL) return nil;
    jsize count = (*env)->GetArrayLength(env, value);
    NSMutableArray<NSString *> *result = [NSMutableArray arrayWithCapacity:(NSUInteger)count];
    for (jsize i = 0; i < count; i++) {
        jstring item = (jstring)(*env)->GetObjectArrayElement(env, value, i);
        NSString *string = kbNSString(env, item);
        if (string != nil) [result addObject:string];
        (*env)->DeleteLocalRef(env, item);
    }
    return result;
}

static void kbEmit(int kind, int64_t a, int64_t b, int status, NSString *text, NSData *data) {
    if (gCallback == NULL) return;
    JNIEnv *env = kbEnv();
    if (env == NULL) return;
    jstring jText = kbJString(env, text);
    jbyteArray jData = NULL;
    if (data != nil) {
        jData = (*env)->NewByteArray(env, (jsize)data.length);
        if (data.length > 0) (*env)->SetByteArrayRegion(env, jData, 0, (jsize)data.length, data.bytes);
    }
    (*env)->CallVoidMethod(env, gCallback, gOnEvent, (jint)kind, (jlong)a, (jlong)b, (jint)status, jText, jData);
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    if (jText != NULL) (*env)->DeleteLocalRef(env, jText);
    if (jData != NULL) (*env)->DeleteLocalRef(env, jData);
}

static void kbGuarded(void (^block)(void)) {
    @try {
        block();
    } @catch (NSException *exception) {
        NSLog(@"kmp-ble: CoreBluetooth rejected a call: %@ %@", exception.name, exception.reason);
    }
}

static void kbSync(void (^block)(void)) {
    if (dispatch_get_specific(kQueueKey) != NULL) {
        kbGuarded(block);
    } else {
        dispatch_sync(gQueue, ^{
            kbGuarded(block);
        });
    }
}

#pragma mark - Handles

static int64_t kbHandleFor(id object) {
    if (object == nil) return 0;
    @synchronized(gObjects) {
        NSNumber *existing = [gHandles objectForKey:object];
        if (existing != nil) return existing.longLongValue;
        NSNumber *handle = @(gNextHandle++);
        gObjects[handle] = object;
        [gHandles setObject:handle forKey:object];
        return handle.longLongValue;
    }
}

static id kbObject(int64_t handle) {
    @synchronized(gObjects) {
        return gObjects[@(handle)];
    }
}

static void kbForget(int64_t handle) {
    @synchronized(gObjects) {
        id object = gObjects[@(handle)];
        if (object == nil) return;
        [gObjects removeObjectForKey:@(handle)];
        [gHandles removeObjectForKey:object];
    }
}

static CBPeripheral *kbOwner(id attribute) {
    if ([attribute isKindOfClass:[CBService class]]) return ((CBService *)attribute).peripheral;
    if ([attribute isKindOfClass:[CBCharacteristic class]]) return ((CBCharacteristic *)attribute).service.peripheral;
    if ([attribute isKindOfClass:[CBDescriptor class]]) return ((CBDescriptor *)attribute).characteristic.service.peripheral;
    return nil;
}

static void kbForgetAttributesOf(CBPeripheral *peripheral) {
    @synchronized(gObjects) {
        NSMutableArray<NSNumber *> *stale = [NSMutableArray array];
        [gObjects enumerateKeysAndObjectsUsingBlock:^(NSNumber *key, id object, BOOL *stop) {
            if (kbOwner(object) == peripheral) [stale addObject:key];
        }];
        for (NSNumber *key in stale) {
            id object = gObjects[key];
            [gObjects removeObjectForKey:key];
            if (object != nil) [gHandles removeObjectForKey:object];
        }
    }
}

static void kbPrunePeripherals(void) {
    NSDate *cutoff = [NSDate dateWithTimeIntervalSinceNow:-KB_PRUNE_AGE_SECONDS];
    @synchronized(gPeripheralsById) {
        for (NSString *identifier in gPeripheralsById.allKeys) {
            if ([gRetainedIds containsObject:identifier]) continue;
            NSDate *seen = gLastSeen[identifier];
            if (seen != nil && [seen compare:cutoff] == NSOrderedDescending) continue;
            CBPeripheral *peripheral = gPeripheralsById[identifier];
            if (peripheral.state != CBPeripheralStateDisconnected) continue;
            [gPeripheralsById removeObjectForKey:identifier];
            [gLastSeen removeObjectForKey:identifier];
            kbForgetAttributesOf(peripheral);
            NSNumber *handle = nil;
            @synchronized(gObjects) {
                handle = [gHandles objectForKey:peripheral];
            }
            if (handle != nil) kbForget(handle.longLongValue);
        }
    }
}

#pragma mark - Status encoding

static int kbStatus(NSError *error) {
    if (error == nil) return 0;
    if ([error.domain isEqualToString:CBATTErrorDomain]) return (int)error.code;
    if ([error.domain isEqualToString:CBErrorDomain]) return 1000 + (int)error.code;
    return -1;
}

static NSString *kbMessage(NSError *error) {
    return error.localizedDescription;
}

#pragma mark - Advertisement reconstruction

static NSData *kbLittleEndian(CBUUID *uuid) {
    NSData *bigEndian = uuid.data;
    NSMutableData *result = [NSMutableData dataWithLength:bigEndian.length];
    const uint8_t *source = bigEndian.bytes;
    uint8_t *target = result.mutableBytes;
    for (NSUInteger i = 0; i < bigEndian.length; i++) target[i] = source[bigEndian.length - 1 - i];
    return result;
}

static void kbAppendStructure(NSMutableData *out, uint8_t type, NSData *payload) {
    if (payload.length > 253) return;
    uint8_t length = (uint8_t)(payload.length + 1);
    [out appendBytes:&length length:1];
    [out appendBytes:&type length:1];
    [out appendData:payload];
}

static void kbAppendUuids(NSMutableData *out, NSArray<CBUUID *> *uuids, uint8_t type16, uint8_t type32, uint8_t type128) {
    NSMutableData *short16 = [NSMutableData data];
    NSMutableData *short32 = [NSMutableData data];
    NSMutableData *long128 = [NSMutableData data];
    for (CBUUID *uuid in uuids) {
        NSData *bytes = kbLittleEndian(uuid);
        if (bytes.length == 2) [short16 appendData:bytes];
        else if (bytes.length == 4) [short32 appendData:bytes];
        else if (bytes.length == 16) [long128 appendData:bytes];
    }
    if (short16.length > 0) kbAppendStructure(out, type16, short16);
    if (short32.length > 0) kbAppendStructure(out, type32, short32);
    if (long128.length > 0) kbAppendStructure(out, type128, long128);
}

static NSData *kbAdvertisingBytes(NSDictionary<NSString *, id> *advertisement) {
    NSMutableData *out = [NSMutableData data];
    NSString *name = advertisement[CBAdvertisementDataLocalNameKey];
    if (name != nil) kbAppendStructure(out, 0x09, [name dataUsingEncoding:NSUTF8StringEncoding]);
    NSNumber *txPower = advertisement[CBAdvertisementDataTxPowerLevelKey];
    if (txPower != nil) {
        int8_t power = (int8_t)txPower.intValue;
        kbAppendStructure(out, 0x0A, [NSData dataWithBytes:&power length:1]);
    }
    NSArray<CBUUID *> *services = advertisement[CBAdvertisementDataServiceUUIDsKey];
    if (services != nil) kbAppendUuids(out, services, 0x03, 0x05, 0x07);
    NSArray<CBUUID *> *solicited = advertisement[CBAdvertisementDataSolicitedServiceUUIDsKey];
    if (solicited != nil) kbAppendUuids(out, solicited, 0x14, 0x1F, 0x15);
    NSDictionary<CBUUID *, NSData *> *serviceData = advertisement[CBAdvertisementDataServiceDataKey];
    for (CBUUID *uuid in serviceData) {
        NSMutableData *payload = [NSMutableData dataWithData:kbLittleEndian(uuid)];
        [payload appendData:serviceData[uuid]];
        uint8_t type = uuid.data.length == 2 ? 0x16 : (uuid.data.length == 4 ? 0x20 : 0x21);
        kbAppendStructure(out, type, payload);
    }
    NSData *manufacturer = advertisement[CBAdvertisementDataManufacturerDataKey];
    if (manufacturer != nil) kbAppendStructure(out, 0xFF, manufacturer);
    return out;
}

static NSString *kbLines(NSArray *items, NSString *(^format)(id item)) {
    NSMutableArray<NSString *> *lines = [NSMutableArray arrayWithCapacity:items.count];
    for (id item in items) [lines addObject:format(item)];
    return [lines componentsJoinedByString:@"\n"];
}

#pragma mark - L2CAP streams

@interface KBL2capChannel : NSObject <NSStreamDelegate>
@property(nonatomic, strong) CBL2CAPChannel *channel;
@property(nonatomic, assign) int64_t handle;
@property(atomic, assign) BOOL closed;
@end

@implementation KBL2capChannel

- (void)attach {
    NSInputStream *input = self.channel.inputStream;
    input.delegate = self;
    [input scheduleInRunLoop:[NSRunLoop currentRunLoop] forMode:NSDefaultRunLoopMode];
    [input open];
}

- (void)detach {
    NSInputStream *input = self.channel.inputStream;
    input.delegate = nil;
    [input removeFromRunLoop:[NSRunLoop currentRunLoop] forMode:NSDefaultRunLoopMode];
    [input close];
}

- (void)finish:(int)status {
    if (self.closed) return;
    self.closed = YES;
    [self detach];
    kbEmit(KB_L2CAP_CLOSED, self.handle, 0, status, nil, nil);
}

- (jint)writeData:(NSData *)data {
    @synchronized(self) {
        if (self.closed) return -1;
        NSOutputStream *output = self.channel.outputStream;
        const uint8_t *bytes = data.bytes;
        NSUInteger written = 0;
        while (written < data.length) {
            NSInteger result = [output write:bytes + written maxLength:data.length - written];
            if (result <= 0) return -1;
            written += (NSUInteger)result;
        }
        return 0;
    }
}

- (void)closeOutput {
    self.closed = YES;
    @synchronized(self) {
        [self.channel.outputStream close];
    }
}

- (void)stream:(NSStream *)stream handleEvent:(NSStreamEvent)event {
    if (event & NSStreamEventHasBytesAvailable) {
        NSInputStream *input = (NSInputStream *)stream;
        uint8_t buffer[KB_L2CAP_READ_BUFFER];
        while (input.hasBytesAvailable) {
            NSInteger read = [input read:buffer maxLength:sizeof(buffer)];
            if (read <= 0) break;
            kbEmit(KB_L2CAP_DATA, self.handle, 0, 0, nil, [NSData dataWithBytes:buffer length:(NSUInteger)read]);
        }
    }
    if (event & NSStreamEventErrorOccurred) {
        [self finish:-1];
    } else if (event & NSStreamEventEndEncountered) {
        [self finish:0];
    }
}

@end

static void kbStartStreamThread(void) {
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        gStreamThread = [[NSThread alloc] initWithBlock:^{
            [[NSRunLoop currentRunLoop] addPort:[NSMachPort port] forMode:NSDefaultRunLoopMode];
            while (YES) {
                @autoreleasepool {
                    [[NSRunLoop currentRunLoop] runMode:NSDefaultRunLoopMode beforeDate:[NSDate distantFuture]];
                }
            }
        }];
        gStreamThread.name = @"kmpble-l2cap";
        [gStreamThread start];
    });
}

static int64_t kbRegisterChannel(CBL2CAPChannel *channel) {
    KBL2capChannel *wrapper = [[KBL2capChannel alloc] init];
    wrapper.channel = channel;
    wrapper.handle = kbHandleFor(wrapper);
    [channel.outputStream open];
    kbStartStreamThread();
    [wrapper performSelector:@selector(attach) onThread:gStreamThread withObject:nil waitUntilDone:NO];
    return wrapper.handle;
}

#pragma mark - Central

@interface KBCentralDelegate : NSObject <CBCentralManagerDelegate, CBPeripheralDelegate>
@end

@implementation KBCentralDelegate

- (void)centralManagerDidUpdateState:(CBCentralManager *)central {
    kbEmit(KB_CENTRAL_STATE, central.state, 0, 0, nil, nil);
}

- (void)centralManager:(CBCentralManager *)central
    didDiscoverPeripheral:(CBPeripheral *)peripheral
        advertisementData:(NSDictionary<NSString *, id> *)advertisementData
                     RSSI:(NSNumber *)RSSI {
    if (RSSI.intValue == 127) return;
    NSString *identifier = peripheral.identifier.UUIDString;
    @synchronized(gPeripheralsById) {
        gPeripheralsById[identifier] = peripheral;
        gLastSeen[identifier] = [NSDate date];
    }
    if (++gDiscoveriesSincePrune >= KB_PRUNE_EVERY) {
        gDiscoveriesSincePrune = 0;
        kbPrunePeripherals();
    }
    peripheral.delegate = self;
    int64_t handle = kbHandleFor(peripheral);
    NSNumber *connectable = advertisementData[CBAdvertisementDataIsConnectable];
    NSString *text = [NSString stringWithFormat:@"%@\n%@", identifier, peripheral.name ?: @""];
    kbEmit(KB_DISCOVERED, handle, connectable == nil ? -1 : connectable.boolValue, RSSI.intValue, text,
           kbAdvertisingBytes(advertisementData));
}

- (void)centralManager:(CBCentralManager *)central didConnectPeripheral:(CBPeripheral *)peripheral {
    kbEmit(KB_CONNECTED, kbHandleFor(peripheral), 0, 0, nil, nil);
}

- (void)centralManager:(CBCentralManager *)central
    didFailToConnectPeripheral:(CBPeripheral *)peripheral
                         error:(NSError *)error {
    kbEmit(KB_CONNECT_FAILED, kbHandleFor(peripheral), 0, kbStatus(error), kbMessage(error), nil);
}

- (void)centralManager:(CBCentralManager *)central
    didDisconnectPeripheral:(CBPeripheral *)peripheral
                      error:(NSError *)error {
    kbForgetAttributesOf(peripheral);
    kbEmit(KB_DISCONNECTED, kbHandleFor(peripheral), 0, kbStatus(error), kbMessage(error), nil);
}

- (void)peripheral:(CBPeripheral *)peripheral didDiscoverServices:(NSError *)error {
    NSString *text = kbLines(peripheral.services ?: @[], ^NSString *(CBService *service) {
        return [NSString stringWithFormat:@"%lld|%@", kbHandleFor(service), service.UUID.UUIDString];
    });
    kbEmit(KB_SERVICES_DISCOVERED, kbHandleFor(peripheral), 0, kbStatus(error), error ? kbMessage(error) : text, nil);
}

- (void)peripheral:(CBPeripheral *)peripheral
    didDiscoverCharacteristicsForService:(CBService *)service
                                   error:(NSError *)error {
    NSString *text = kbLines(service.characteristics ?: @[], ^NSString *(CBCharacteristic *characteristic) {
        return [NSString stringWithFormat:@"%lld|%@|%lu", kbHandleFor(characteristic), characteristic.UUID.UUIDString,
                                          (unsigned long)characteristic.properties];
    });
    kbEmit(KB_CHARACTERISTICS_DISCOVERED, kbHandleFor(peripheral), kbHandleFor(service), kbStatus(error),
           error ? kbMessage(error) : text, nil);
}

- (void)peripheral:(CBPeripheral *)peripheral
    didDiscoverDescriptorsForCharacteristic:(CBCharacteristic *)characteristic
                                      error:(NSError *)error {
    NSString *text = kbLines(characteristic.descriptors ?: @[], ^NSString *(CBDescriptor *descriptor) {
        return [NSString stringWithFormat:@"%lld|%@", kbHandleFor(descriptor), descriptor.UUID.UUIDString];
    });
    kbEmit(KB_DESCRIPTORS_DISCOVERED, kbHandleFor(peripheral), kbHandleFor(characteristic), kbStatus(error),
           error ? kbMessage(error) : text, nil);
}

- (void)peripheral:(CBPeripheral *)peripheral
    didUpdateValueForCharacteristic:(CBCharacteristic *)characteristic
                              error:(NSError *)error {
    kbEmit(KB_VALUE_UPDATED, kbHandleFor(peripheral), kbHandleFor(characteristic), kbStatus(error), kbMessage(error),
           characteristic.value ?: [NSData data]);
}

- (void)peripheral:(CBPeripheral *)peripheral
    didWriteValueForCharacteristic:(CBCharacteristic *)characteristic
                             error:(NSError *)error {
    kbEmit(KB_VALUE_WRITTEN, kbHandleFor(peripheral), kbHandleFor(characteristic), kbStatus(error), kbMessage(error), nil);
}

- (void)peripheral:(CBPeripheral *)peripheral
    didUpdateValueForDescriptor:(CBDescriptor *)descriptor
                          error:(NSError *)error {
    NSData *value = nil;
    id raw = descriptor.value;
    if ([raw isKindOfClass:[NSData class]]) {
        value = raw;
    } else if ([raw isKindOfClass:[NSString class]]) {
        value = [(NSString *)raw dataUsingEncoding:NSUTF8StringEncoding];
    } else if ([raw isKindOfClass:[NSNumber class]]) {
        uint16_t number = CFSwapInt16HostToLittle((uint16_t)[(NSNumber *)raw unsignedShortValue]);
        value = [NSData dataWithBytes:&number length:sizeof(number)];
    }
    kbEmit(KB_DESCRIPTOR_VALUE, kbHandleFor(peripheral), kbHandleFor(descriptor), kbStatus(error), kbMessage(error),
           value ?: [NSData data]);
}

- (void)peripheral:(CBPeripheral *)peripheral
    didWriteValueForDescriptor:(CBDescriptor *)descriptor
                         error:(NSError *)error {
    kbEmit(KB_DESCRIPTOR_WRITTEN, kbHandleFor(peripheral), kbHandleFor(descriptor), kbStatus(error), kbMessage(error), nil);
}

- (void)peripheral:(CBPeripheral *)peripheral
    didUpdateNotificationStateForCharacteristic:(CBCharacteristic *)characteristic
                                          error:(NSError *)error {
    uint8_t notifying = characteristic.isNotifying ? 1 : 0;
    kbEmit(KB_NOTIFY_STATE, kbHandleFor(peripheral), kbHandleFor(characteristic), kbStatus(error), kbMessage(error),
           [NSData dataWithBytes:&notifying length:1]);
}

- (void)peripheral:(CBPeripheral *)peripheral didReadRSSI:(NSNumber *)RSSI error:(NSError *)error {
    kbEmit(KB_RSSI, kbHandleFor(peripheral), RSSI.longLongValue, kbStatus(error), kbMessage(error), nil);
}

- (void)peripheralIsReadyToSendWriteWithoutResponse:(CBPeripheral *)peripheral {
    kbEmit(KB_READY_TO_WRITE, kbHandleFor(peripheral), 0, 0, nil, nil);
}

- (void)peripheral:(CBPeripheral *)peripheral didModifyServices:(NSArray<CBService *> *)invalidatedServices {
    kbEmit(KB_SERVICES_MODIFIED, kbHandleFor(peripheral), 0, 0, nil, nil);
}

- (void)peripheral:(CBPeripheral *)peripheral didOpenL2CAPChannel:(CBL2CAPChannel *)channel error:(NSError *)error {
    int64_t handle = (channel != nil && error == nil) ? kbRegisterChannel(channel) : 0;
    kbEmit(KB_L2CAP_OPENED, kbHandleFor(peripheral), handle, kbStatus(error), kbMessage(error), nil);
}

@end

#pragma mark - Peripheral manager

@interface KBPeripheralManagerDelegate : NSObject <CBPeripheralManagerDelegate>
@end

@implementation KBPeripheralManagerDelegate

static NSString *kbRememberCentral(CBCentral *central) {
    NSString *identifier = central.identifier.UUIDString;
    @synchronized(gCentralsById) {
        gCentralsById[identifier] = central;
    }
    return identifier;
}

- (void)peripheralManagerDidUpdateState:(CBPeripheralManager *)peripheral {
    kbEmit(KB_PM_STATE, peripheral.state, 0, 0, nil, nil);
}

- (void)peripheralManager:(CBPeripheralManager *)peripheral didAddService:(CBService *)service error:(NSError *)error {
    kbEmit(KB_PM_SERVICE_ADDED, kbHandleFor(service), 0, kbStatus(error), kbMessage(error), nil);
}

- (void)peripheralManagerDidStartAdvertising:(CBPeripheralManager *)peripheral error:(NSError *)error {
    kbEmit(KB_PM_ADVERTISING_STARTED, 0, 0, kbStatus(error), kbMessage(error), nil);
}

- (void)peripheralManager:(CBPeripheralManager *)peripheral didReceiveReadRequest:(CBATTRequest *)request {
    NSString *central = kbRememberCentral(request.central);
    kbEmit(KB_PM_READ_REQUEST, kbHandleFor(request), kbHandleFor(request.characteristic), (int)request.offset, central, nil);
}

- (void)peripheralManager:(CBPeripheralManager *)peripheral didReceiveWriteRequests:(NSArray<CBATTRequest *> *)requests {
    if (requests.count == 0) return;
    NSMutableData *batch = [NSMutableData data];
    for (CBATTRequest *request in requests) {
        int64_t characteristic = CFSwapInt64HostToBig((uint64_t)kbHandleFor(request.characteristic));
        uint32_t offset = CFSwapInt32HostToBig((uint32_t)request.offset);
        NSData *value = request.value ?: [NSData data];
        uint32_t length = CFSwapInt32HostToBig((uint32_t)value.length);
        [batch appendBytes:&characteristic length:sizeof(characteristic)];
        [batch appendBytes:&offset length:sizeof(offset)];
        [batch appendBytes:&length length:sizeof(length)];
        [batch appendData:value];
    }
    CBATTRequest *first = requests.firstObject;
    NSString *central = kbRememberCentral(first.central);
    kbEmit(KB_PM_WRITE_REQUESTS, kbHandleFor(first), (int64_t)requests.count, 0, central, batch);
}

- (void)peripheralManager:(CBPeripheralManager *)peripheral
                         central:(CBCentral *)central
    didSubscribeToCharacteristic:(CBCharacteristic *)characteristic {
    NSString *identifier = kbRememberCentral(central);
    kbEmit(KB_PM_SUBSCRIBED, (int64_t)central.maximumUpdateValueLength, kbHandleFor(characteristic), 0, identifier, nil);
}

- (void)peripheralManager:(CBPeripheralManager *)peripheral
                             central:(CBCentral *)central
    didUnsubscribeFromCharacteristic:(CBCharacteristic *)characteristic {
    NSString *identifier = kbRememberCentral(central);
    kbEmit(KB_PM_UNSUBSCRIBED, 0, kbHandleFor(characteristic), 0, identifier, nil);
}

- (void)peripheralManagerIsReadyToUpdateSubscribers:(CBPeripheralManager *)peripheral {
    kbEmit(KB_PM_READY_TO_UPDATE, 0, 0, 0, nil, nil);
}

- (void)peripheralManager:(CBPeripheralManager *)peripheral didPublishL2CAPChannel:(CBL2CAPPSM)PSM error:(NSError *)error {
    kbEmit(KB_PM_L2CAP_PUBLISHED, PSM, 0, kbStatus(error), kbMessage(error), nil);
}

- (void)peripheralManager:(CBPeripheralManager *)peripheral didUnpublishL2CAPChannel:(CBL2CAPPSM)PSM error:(NSError *)error {
}

- (void)peripheralManager:(CBPeripheralManager *)peripheral didOpenL2CAPChannel:(CBL2CAPChannel *)channel error:(NSError *)error {
    int64_t handle = (channel != nil && error == nil) ? kbRegisterChannel(channel) : 0;
    kbEmit(KB_PM_L2CAP_OPENED, channel != nil ? channel.PSM : 0, handle, kbStatus(error), kbMessage(error), nil);
}

@end

#pragma mark - Helpers

// TCC checks the Info.plist of the process macOS holds responsible for this one (the app
// that launched the JVM), not the JVM's own, and aborts the process when the key is missing.
static NSString *kbResponsibleExecutable(void) {
    pid_t (*responsible)(pid_t) = dlsym(RTLD_DEFAULT, "responsibility_get_pid_responsible_for_pid");
    if (responsible == NULL) return nil;
    pid_t pid = responsible(getpid());
    if (pid <= 0) return nil;
    char path[PROC_PIDPATHINFO_MAXSIZE];
    if (proc_pidpath(pid, path, sizeof(path)) <= 0) return nil;
    return [NSString stringWithUTF8String:path];
}

static BOOL kbIsPlatformPath(NSString *path) {
    for (NSString *prefix in @[ @"/System/", @"/usr/bin/", @"/usr/sbin/", @"/usr/libexec/", @"/bin/", @"/sbin/" ]) {
        if ([path hasPrefix:prefix]) return YES;
    }
    return NO;
}

static NSString *kbBundlePathOf(NSString *executable) {
    NSString *macos = executable.stringByDeletingLastPathComponent;
    NSString *contents = macos.stringByDeletingLastPathComponent;
    NSString *bundle = contents.stringByDeletingLastPathComponent;
    BOOL inBundle = [macos.lastPathComponent isEqualToString:@"MacOS"] &&
                    [contents.lastPathComponent isEqualToString:@"Contents"] &&
                    [bundle.pathExtension isEqualToString:@"app"];
    return inBundle ? bundle : nil;
}

static NSString *kbMissingUsageDescriptionHost(void) {
    NSString *executable = kbResponsibleExecutable();
    if (executable == nil) {
        NSBundle *main = NSBundle.mainBundle;
        if (![main.bundlePath hasSuffix:@".app"] || [main objectForInfoDictionaryKey:kUsageDescriptionKey] != nil) return nil;
        return main.bundlePath;
    }
    if (kbIsPlatformPath(executable)) return nil;
    NSString *bundle = kbBundlePathOf(executable);
    NSDictionary *info = bundle != nil
        ? [NSBundle bundleWithPath:bundle].infoDictionary
        : CFBridgingRelease(CFBundleCopyInfoDictionaryForURL((__bridge CFURLRef)[NSURL fileURLWithPath:executable]));
    if (info == nil || info[kUsageDescriptionKey] != nil) return nil;
    return bundle ?: executable;
}

static CBPeripheral *kbPeripheral(jlong handle) {
    id object = kbObject(handle);
    return [object isKindOfClass:[CBPeripheral class]] ? object : nil;
}

static NSArray<CBUUID *> *kbUuids(NSArray<NSString *> *strings) {
    if (strings == nil) return nil;
    NSMutableArray<CBUUID *> *result = [NSMutableArray arrayWithCapacity:strings.count];
    for (NSString *string in strings) [result addObject:[CBUUID UUIDWithString:string]];
    return result;
}

#define KB_JNI(ret, name) JNIEXPORT ret JNICALL Java_com_atruedev_kmpble_macos_internal_NativeBridge_##name

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    gJvm = vm;
    pthread_key_create(&gDetachKey, kbDetachThread);
    return JNI_VERSION_1_8;
}

KB_JNI(jint, nativeInit)(JNIEnv *env, jclass cls, jobject callback) {
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        gQueue = dispatch_queue_create("com.atruedev.kmpble.corebluetooth", DISPATCH_QUEUE_SERIAL);
        dispatch_queue_set_specific(gQueue, kQueueKey, kQueueKey, NULL);
        gObjects = [NSMutableDictionary dictionary];
        gHandles = [NSMapTable mapTableWithKeyOptions:NSPointerFunctionsStrongMemory | NSPointerFunctionsObjectPointerPersonality
                                         valueOptions:NSPointerFunctionsStrongMemory];
        gPeripheralsById = [NSMutableDictionary dictionary];
        gCentralsById = [NSMutableDictionary dictionary];
        gLastSeen = [NSMutableDictionary dictionary];
        gRetainedIds = [NSMutableSet set];
    });
    if (gCallback == NULL && callback != NULL) {
        gCallback = (*env)->NewGlobalRef(env, callback);
        jclass callbackClass = (*env)->GetObjectClass(env, callback);
        gOnEvent = (*env)->GetMethodID(env, callbackClass, "onEvent", "(IJJILjava/lang/String;[B)V");
        (*env)->DeleteLocalRef(env, callbackClass);
    }
    return KB_API_VERSION;
}

KB_JNI(jstring, nativeMissingUsageDescriptionHost)(JNIEnv *env, jclass cls) {
    return kbJString(env, kbMissingUsageDescriptionHost());
}

KB_JNI(jint, nativeAuthorization)(JNIEnv *env, jclass cls) {
    return (jint)CBManager.authorization;
}

KB_JNI(void, nativeCentralStart)(JNIEnv *env, jclass cls) {
    kbSync(^{
        if (gCentral != nil) return;
        gCentralDelegate = [[KBCentralDelegate alloc] init];
        gCentral = [[CBCentralManager alloc] initWithDelegate:gCentralDelegate
                                                        queue:gQueue
                                                      options:@{CBCentralManagerOptionShowPowerAlertKey : @NO}];
    });
}

KB_JNI(jint, nativeCentralState)(JNIEnv *env, jclass cls) {
    __block jint state = CBManagerStateUnknown;
    kbSync(^{
        if (gCentral != nil) state = (jint)gCentral.state;
    });
    return state;
}

KB_JNI(void, nativeScanStart)(JNIEnv *env, jclass cls, jobjectArray serviceUuids) {
    NSArray<CBUUID *> *uuids = kbUuids(kbStringArray(env, serviceUuids));
    kbSync(^{
        [gCentral scanForPeripheralsWithServices:uuids.count > 0 ? uuids : nil
                                         options:@{CBCentralManagerScanOptionAllowDuplicatesKey : @YES}];
    });
}

KB_JNI(void, nativeScanStop)(JNIEnv *env, jclass cls) {
    kbSync(^{
        [gCentral stopScan];
    });
}

KB_JNI(jlong, nativePeripheralForIdentifier)(JNIEnv *env, jclass cls, jstring identifier) {
    NSString *uuidString = kbNSString(env, identifier);
    __block int64_t handle = 0;
    kbSync(^{
        CBPeripheral *peripheral = nil;
        @synchronized(gPeripheralsById) {
            peripheral = gPeripheralsById[uuidString];
        }
        if (peripheral == nil && gCentral != nil) {
            NSUUID *uuid = [[NSUUID alloc] initWithUUIDString:uuidString];
            if (uuid != nil) peripheral = [gCentral retrievePeripheralsWithIdentifiers:@[ uuid ]].firstObject;
            if (peripheral != nil) {
                @synchronized(gPeripheralsById) {
                    gPeripheralsById[uuidString] = peripheral;
                }
            }
        }
        if (peripheral == nil) return;
        @synchronized(gPeripheralsById) {
            [gRetainedIds addObject:uuidString];
        }
        peripheral.delegate = gCentralDelegate;
        handle = kbHandleFor(peripheral);
    });
    return handle;
}

KB_JNI(jint, nativePeripheralState)(JNIEnv *env, jclass cls, jlong handle) {
    __block jint state = -1;
    kbSync(^{
        CBPeripheral *peripheral = kbPeripheral(handle);
        if (peripheral != nil) state = (jint)peripheral.state;
    });
    return state;
}

KB_JNI(jboolean, nativeConnect)(JNIEnv *env, jclass cls, jlong handle) {
    __block jboolean accepted = JNI_FALSE;
    kbSync(^{
        CBPeripheral *peripheral = kbPeripheral(handle);
        if (peripheral == nil || gCentral == nil || gCentral.state != CBManagerStatePoweredOn) return;
        peripheral.delegate = gCentralDelegate;
        [gCentral connectPeripheral:peripheral options:nil];
        accepted = JNI_TRUE;
    });
    return accepted;
}

KB_JNI(void, nativeCancelConnect)(JNIEnv *env, jclass cls, jlong handle) {
    kbSync(^{
        CBPeripheral *peripheral = kbPeripheral(handle);
        if (peripheral == nil || gCentral == nil || gCentral.state != CBManagerStatePoweredOn) return;
        [gCentral cancelPeripheralConnection:peripheral];
    });
}

KB_JNI(void, nativeReleasePeripheral)(JNIEnv *env, jclass cls, jlong handle) {
    kbSync(^{
        CBPeripheral *peripheral = kbPeripheral(handle);
        if (peripheral == nil) return;
        kbForgetAttributesOf(peripheral);
        @synchronized(gPeripheralsById) {
            [gRetainedIds removeObject:peripheral.identifier.UUIDString];
        }
    });
}

KB_JNI(jboolean, nativeDiscoverServices)(JNIEnv *env, jclass cls, jlong handle) {
    __block jboolean accepted = JNI_FALSE;
    kbSync(^{
        CBPeripheral *peripheral = kbPeripheral(handle);
        if (peripheral == nil || peripheral.state != CBPeripheralStateConnected) return;
        kbForgetAttributesOf(peripheral);
        [peripheral discoverServices:nil];
        accepted = JNI_TRUE;
    });
    return accepted;
}

KB_JNI(jboolean, nativeDiscoverCharacteristics)(JNIEnv *env, jclass cls, jlong handle, jlong serviceHandle) {
    __block jboolean accepted = JNI_FALSE;
    kbSync(^{
        CBPeripheral *peripheral = kbPeripheral(handle);
        id service = kbObject(serviceHandle);
        if (peripheral == nil || ![service isKindOfClass:[CBService class]] || peripheral.state != CBPeripheralStateConnected) return;
        [peripheral discoverCharacteristics:nil forService:service];
        accepted = JNI_TRUE;
    });
    return accepted;
}

KB_JNI(jboolean, nativeDiscoverDescriptors)(JNIEnv *env, jclass cls, jlong handle, jlong characteristicHandle) {
    __block jboolean accepted = JNI_FALSE;
    kbSync(^{
        CBPeripheral *peripheral = kbPeripheral(handle);
        id characteristic = kbObject(characteristicHandle);
        if (peripheral == nil || ![characteristic isKindOfClass:[CBCharacteristic class]] ||
            peripheral.state != CBPeripheralStateConnected)
            return;
        [peripheral discoverDescriptorsForCharacteristic:characteristic];
        accepted = JNI_TRUE;
    });
    return accepted;
}

KB_JNI(jboolean, nativeReadCharacteristic)(JNIEnv *env, jclass cls, jlong handle, jlong characteristicHandle) {
    __block jboolean accepted = JNI_FALSE;
    kbSync(^{
        CBPeripheral *peripheral = kbPeripheral(handle);
        id characteristic = kbObject(characteristicHandle);
        if (peripheral == nil || ![characteristic isKindOfClass:[CBCharacteristic class]] ||
            peripheral.state != CBPeripheralStateConnected)
            return;
        [peripheral readValueForCharacteristic:characteristic];
        accepted = JNI_TRUE;
    });
    return accepted;
}

KB_JNI(jboolean, nativeWriteCharacteristic)
(JNIEnv *env, jclass cls, jlong handle, jlong characteristicHandle, jbyteArray value, jboolean withResponse) {
    NSData *data = kbNSData(env, value);
    __block jboolean accepted = JNI_FALSE;
    kbSync(^{
        CBPeripheral *peripheral = kbPeripheral(handle);
        id characteristic = kbObject(characteristicHandle);
        if (peripheral == nil || ![characteristic isKindOfClass:[CBCharacteristic class]] ||
            peripheral.state != CBPeripheralStateConnected)
            return;
        CBCharacteristicWriteType type = withResponse ? CBCharacteristicWriteWithResponse : CBCharacteristicWriteWithoutResponse;
        [peripheral writeValue:data forCharacteristic:characteristic type:type];
        accepted = JNI_TRUE;
    });
    return accepted;
}

KB_JNI(jboolean, nativeCanSendWriteWithoutResponse)(JNIEnv *env, jclass cls, jlong handle) {
    __block jboolean ready = JNI_FALSE;
    kbSync(^{
        CBPeripheral *peripheral = kbPeripheral(handle);
        ready = peripheral != nil && peripheral.canSendWriteWithoutResponse;
    });
    return ready;
}

KB_JNI(jboolean, nativeReadDescriptor)(JNIEnv *env, jclass cls, jlong handle, jlong descriptorHandle) {
    __block jboolean accepted = JNI_FALSE;
    kbSync(^{
        CBPeripheral *peripheral = kbPeripheral(handle);
        id descriptor = kbObject(descriptorHandle);
        if (peripheral == nil || ![descriptor isKindOfClass:[CBDescriptor class]] || peripheral.state != CBPeripheralStateConnected)
            return;
        [peripheral readValueForDescriptor:descriptor];
        accepted = JNI_TRUE;
    });
    return accepted;
}

KB_JNI(jboolean, nativeWriteDescriptor)(JNIEnv *env, jclass cls, jlong handle, jlong descriptorHandle, jbyteArray value) {
    NSData *data = kbNSData(env, value);
    __block jboolean accepted = JNI_FALSE;
    kbSync(^{
        CBPeripheral *peripheral = kbPeripheral(handle);
        id descriptor = kbObject(descriptorHandle);
        if (peripheral == nil || ![descriptor isKindOfClass:[CBDescriptor class]] || peripheral.state != CBPeripheralStateConnected)
            return;
        [peripheral writeValue:data forDescriptor:descriptor];
        accepted = JNI_TRUE;
    });
    return accepted;
}

KB_JNI(jboolean, nativeSetNotify)(JNIEnv *env, jclass cls, jlong handle, jlong characteristicHandle, jboolean enabled) {
    __block jboolean accepted = JNI_FALSE;
    kbSync(^{
        CBPeripheral *peripheral = kbPeripheral(handle);
        id characteristic = kbObject(characteristicHandle);
        if (peripheral == nil || ![characteristic isKindOfClass:[CBCharacteristic class]] ||
            peripheral.state != CBPeripheralStateConnected)
            return;
        [peripheral setNotifyValue:enabled forCharacteristic:characteristic];
        accepted = JNI_TRUE;
    });
    return accepted;
}

KB_JNI(jboolean, nativeReadRssi)(JNIEnv *env, jclass cls, jlong handle) {
    __block jboolean accepted = JNI_FALSE;
    kbSync(^{
        CBPeripheral *peripheral = kbPeripheral(handle);
        if (peripheral == nil || peripheral.state != CBPeripheralStateConnected) return;
        [peripheral readRSSI];
        accepted = JNI_TRUE;
    });
    return accepted;
}

KB_JNI(jint, nativeMaximumWriteLength)(JNIEnv *env, jclass cls, jlong handle, jboolean withResponse) {
    __block jint length = -1;
    kbSync(^{
        CBPeripheral *peripheral = kbPeripheral(handle);
        if (peripheral == nil) return;
        CBCharacteristicWriteType type = withResponse ? CBCharacteristicWriteWithResponse : CBCharacteristicWriteWithoutResponse;
        length = (jint)[peripheral maximumWriteValueLengthForType:type];
    });
    return length;
}

KB_JNI(jboolean, nativeOpenL2cap)(JNIEnv *env, jclass cls, jlong handle, jint psm) {
    __block jboolean accepted = JNI_FALSE;
    kbSync(^{
        CBPeripheral *peripheral = kbPeripheral(handle);
        if (peripheral == nil || peripheral.state != CBPeripheralStateConnected) return;
        [peripheral openL2CAPChannel:(CBL2CAPPSM)psm];
        accepted = JNI_TRUE;
    });
    return accepted;
}

KB_JNI(jint, nativeL2capWrite)(JNIEnv *env, jclass cls, jlong handle, jbyteArray value) {
    NSData *data = kbNSData(env, value);
    id object = kbObject(handle);
    if (![object isKindOfClass:[KBL2capChannel class]]) return -1;
    return [(KBL2capChannel *)object writeData:data];
}

KB_JNI(void, nativeL2capClose)(JNIEnv *env, jclass cls, jlong handle) {
    id object = kbObject(handle);
    if (![object isKindOfClass:[KBL2capChannel class]]) return;
    KBL2capChannel *wrapper = object;
    [wrapper performSelector:@selector(detach) onThread:gStreamThread withObject:nil waitUntilDone:NO];
    [wrapper closeOutput];
    kbForget(handle);
}

KB_JNI(jint, nativeL2capPsm)(JNIEnv *env, jclass cls, jlong handle) {
    id object = kbObject(handle);
    if (![object isKindOfClass:[KBL2capChannel class]]) return 0;
    return ((KBL2capChannel *)object).channel.PSM;
}

KB_JNI(void, nativePmStart)(JNIEnv *env, jclass cls) {
    kbSync(^{
        if (gPeripheralManager != nil) return;
        gPeripheralManagerDelegate = [[KBPeripheralManagerDelegate alloc] init];
        gPeripheralManager = [[CBPeripheralManager alloc] initWithDelegate:gPeripheralManagerDelegate
                                                                     queue:gQueue
                                                                   options:@{CBPeripheralManagerOptionShowPowerAlertKey : @NO}];
    });
}

KB_JNI(jint, nativePmState)(JNIEnv *env, jclass cls) {
    __block jint state = CBManagerStateUnknown;
    kbSync(^{
        if (gPeripheralManager != nil) state = (jint)gPeripheralManager.state;
    });
    return state;
}

KB_JNI(jlongArray, nativePmAddService)
(JNIEnv *env, jclass cls, jstring serviceUuid, jobjectArray characteristicUuids, jintArray properties, jintArray permissions) {
    NSString *uuid = kbNSString(env, serviceUuid);
    NSArray<NSString *> *characteristics = kbStringArray(env, characteristicUuids);
    jsize count = (*env)->GetArrayLength(env, properties);
    jint *propertyValues = (*env)->GetIntArrayElements(env, properties, NULL);
    jint *permissionValues = (*env)->GetIntArrayElements(env, permissions, NULL);
    NSMutableArray<NSNumber *> *props = [NSMutableArray arrayWithCapacity:(NSUInteger)count];
    NSMutableArray<NSNumber *> *perms = [NSMutableArray arrayWithCapacity:(NSUInteger)count];
    for (jsize i = 0; i < count; i++) {
        [props addObject:@(propertyValues[i])];
        [perms addObject:@(permissionValues[i])];
    }
    (*env)->ReleaseIntArrayElements(env, properties, propertyValues, JNI_ABORT);
    (*env)->ReleaseIntArrayElements(env, permissions, permissionValues, JNI_ABORT);

    NSMutableArray<NSNumber *> *handles = [NSMutableArray arrayWithCapacity:characteristics.count + 1];
    kbSync(^{
        if (gPeripheralManager == nil) return;
        CBMutableService *service = [[CBMutableService alloc] initWithType:[CBUUID UUIDWithString:uuid] primary:YES];
        NSMutableArray<CBMutableCharacteristic *> *mutableCharacteristics = [NSMutableArray array];
        [handles addObject:@(kbHandleFor(service))];
        for (NSUInteger i = 0; i < characteristics.count; i++) {
            CBMutableCharacteristic *characteristic =
                [[CBMutableCharacteristic alloc] initWithType:[CBUUID UUIDWithString:characteristics[i]]
                                                   properties:(CBCharacteristicProperties)props[i].unsignedIntegerValue
                                                        value:nil
                                                  permissions:(CBAttributePermissions)perms[i].unsignedIntegerValue];
            [mutableCharacteristics addObject:characteristic];
            [handles addObject:@(kbHandleFor(characteristic))];
        }
        service.characteristics = mutableCharacteristics;
        [gPeripheralManager addService:service];
    });
    if (handles.count == 0) return NULL;
    jlongArray result = (*env)->NewLongArray(env, (jsize)handles.count);
    jlong *values = malloc(sizeof(jlong) * handles.count);
    for (NSUInteger i = 0; i < handles.count; i++) values[i] = handles[i].longLongValue;
    (*env)->SetLongArrayRegion(env, result, 0, (jsize)handles.count, values);
    free(values);
    return result;
}

KB_JNI(void, nativePmRemoveService)(JNIEnv *env, jclass cls, jlong handle) {
    kbSync(^{
        id service = kbObject(handle);
        if (gPeripheralManager == nil || ![service isKindOfClass:[CBMutableService class]]) return;
        for (CBCharacteristic *characteristic in ((CBMutableService *)service).characteristics) {
            NSNumber *characteristicHandle = nil;
            @synchronized(gObjects) {
                characteristicHandle = [gHandles objectForKey:characteristic];
            }
            if (characteristicHandle != nil) kbForget(characteristicHandle.longLongValue);
        }
        [gPeripheralManager removeService:service];
        kbForget(handle);
    });
}

KB_JNI(void, nativePmRespond)(JNIEnv *env, jclass cls, jlong requestHandle, jint result, jbyteArray value) {
    NSData *data = kbNSData(env, value);
    kbSync(^{
        id request = kbObject(requestHandle);
        if (gPeripheralManager == nil || ![request isKindOfClass:[CBATTRequest class]]) return;
        if (data != nil) ((CBATTRequest *)request).value = data;
        [gPeripheralManager respondToRequest:request withResult:(CBATTError)result];
        kbForget(requestHandle);
    });
}

KB_JNI(jint, nativePmUpdateValue)(JNIEnv *env, jclass cls, jlong characteristicHandle, jbyteArray value, jstring centralId) {
    NSData *data = kbNSData(env, value);
    NSString *central = kbNSString(env, centralId);
    __block jint sent = -1;
    kbSync(^{
        id characteristic = kbObject(characteristicHandle);
        if (gPeripheralManager == nil || ![characteristic isKindOfClass:[CBMutableCharacteristic class]]) return;
        NSArray<CBCentral *> *targets = nil;
        if (central != nil) {
            CBCentral *target = nil;
            @synchronized(gCentralsById) {
                target = gCentralsById[central];
            }
            if (target == nil) return;
            targets = @[ target ];
        }
        sent = [gPeripheralManager updateValue:data forCharacteristic:characteristic onSubscribedCentrals:targets] ? 1 : 0;
    });
    return sent;
}

KB_JNI(void, nativePmStartAdvertising)(JNIEnv *env, jclass cls, jstring name, jobjectArray serviceUuids) {
    NSString *localName = kbNSString(env, name);
    NSArray<CBUUID *> *uuids = kbUuids(kbStringArray(env, serviceUuids));
    kbSync(^{
        if (gPeripheralManager == nil) return;
        NSMutableDictionary<NSString *, id> *advertisement = [NSMutableDictionary dictionary];
        if (localName != nil) advertisement[CBAdvertisementDataLocalNameKey] = localName;
        if (uuids.count > 0) advertisement[CBAdvertisementDataServiceUUIDsKey] = uuids;
        [gPeripheralManager startAdvertising:advertisement];
    });
}

KB_JNI(void, nativePmStopAdvertising)(JNIEnv *env, jclass cls) {
    kbSync(^{
        [gPeripheralManager stopAdvertising];
    });
}

KB_JNI(void, nativePmPublishL2cap)(JNIEnv *env, jclass cls, jboolean encrypted) {
    kbSync(^{
        [gPeripheralManager publishL2CAPChannelWithEncryption:encrypted];
    });
}

KB_JNI(void, nativePmUnpublishL2cap)(JNIEnv *env, jclass cls, jint psm) {
    kbSync(^{
        [gPeripheralManager unpublishL2CAPChannel:(CBL2CAPPSM)psm];
    });
}
