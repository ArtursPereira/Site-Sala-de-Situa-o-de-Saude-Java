local count = tonumber(redis.call('GET', KEYS[1]) or '0')
local limit = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
if count >= limit then
  return math.max(1, redis.call('PTTL', KEYS[1]))
end
if count == 0 then
  redis.call('SET', KEYS[1], '1', 'PX', window)
else
  redis.call('INCR', KEYS[1])
end
return 0
