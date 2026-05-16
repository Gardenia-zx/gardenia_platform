local cooldownMs = tonumber(ARGV[1])
local dailyLimit = tonumber(ARGV[2])
local dailyTtlMs = tonumber(ARGV[3])

-- 冷却时间检查
if cooldownMs > 0 and redis.call('EXISTS', KEYS[1]) == 1 then
    return 1
end

-- 每日限额检查
if dailyLimit > 0 then
    local current = tonumber(redis.call('GET', KEYS[2]) or '0')
    if current >= dailyLimit then
        return 2
    end

    local newCount = redis.call('INCR', KEYS[2])
    if newCount == 1 and dailyTtlMs > 0 then
        redis.call('PEXPIRE', KEYS[2], dailyTtlMs)
    end
end

-- 占用冷却窗口
if cooldownMs > 0 then
    redis.call('SET', KEYS[1], '1', 'PX', cooldownMs)
end

return 0