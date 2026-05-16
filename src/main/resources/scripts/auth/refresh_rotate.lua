if redis.call('GET', KEYS[1]) ~= '1' then
    return 0
end

redis.call('DEL', KEYS[1])
redis.call('SET', KEYS[2], '1', 'PX', ARGV[1])

return 1