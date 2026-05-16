-- KEYS[1] = cooldown key
-- KEYS[2] = daily count key

-- 删除冷却 key
redis.call('DEL', KEYS[1])

-- 回滚每日计数
local current = tonumber(redis.call('GET', KEYS[2]) or '0')
if current > 1 then
    redis.call('DECR', KEYS[2])
elseif current == 1 then
    redis.call('DEL', KEYS[2])
end

return 1