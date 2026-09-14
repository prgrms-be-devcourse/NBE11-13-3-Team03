-- KEYS:
-- [1] stockKey
-- [2] userKey
-- [3] processedEventKey
--
-- ARGV:
-- [1] quantity
-- [2] processedEventTtlSeconds

local stockKey = KEYS[1]
local userKey = KEYS[2]
local processedEventKey = KEYS[3]

local quantity = tonumber(ARGV[1])
local processedEventTtlSeconds = tonumber(ARGV[2])

-- 이미 처리한 이벤트면 중복 복구하지 않음
if redis.call('EXISTS', processedEventKey) == 1 then
    return 0
end

-- stock Key가 유실된 경우
if redis.call('EXISTS', stockKey) == 0 then
    return -1
end

local userPurchased = redis.call('GET', userKey)

-- 복구할 구매 기록이 없어도 해당 이벤트는 처리 완료로 기록
if not userPurchased then
    redis.call(
        'SET',
        processedEventKey,
        '1',
        'EX',
        processedEventTtlSeconds
    )
    return 0
end

userPurchased = tonumber(userPurchased)

if userPurchased <= 0 then
    redis.call('DEL', userKey)

    redis.call(
        'SET',
        processedEventKey,
        '1',
        'EX',
        processedEventTtlSeconds
    )

    return 0
end

local restoreQuantity = math.min(quantity, userPurchased)

redis.call('INCRBY', stockKey, restoreQuantity)

local remaining = redis.call(
        'DECRBY',
        userKey,
        restoreQuantity
)

if remaining <= 0 then
    redis.call('DEL', userKey)
end

redis.call(
    'SET',
    processedEventKey,
    '1',
    'EX',
    processedEventTtlSeconds
)

return restoreQuantity