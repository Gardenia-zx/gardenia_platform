local key = KEYS[1]
local inputCode = ARGV[1]
local lockTtlMs = tonumber(ARGV[2])

if redis.call('EXISTS', key) == 0 then
    return 'NOT_FOUND|0|0'
end

local storedCode = redis.call('HGET', key, 'code')
local maxAttempts = tonumber(redis.call('HGET', key, 'maxAttempts') or '5')
local attempts = tonumber(redis.call('HGET', key, 'attempts') or '0')

if attempts >= maxAttempts then
    return 'TOO_MANY_ATTEMPTS|' .. attempts .. '|' .. maxAttempts
end

if storedCode == inputCode then
    redis.call('DEL', key)
    return 'SUCCESS|' .. attempts .. '|' .. maxAttempts
end

local updatedAttempts = redis.call('HINCRBY', key, 'attempts', 1)

if updatedAttempts >= maxAttempts then
    if lockTtlMs > 0 then
        redis.call('PEXPIRE', key, lockTtlMs)
    end
    return 'TOO_MANY_ATTEMPTS|' .. updatedAttempts .. '|' .. maxAttempts
end

return 'MISMATCH|' .. updatedAttempts .. '|' .. maxAttempts