local family = redis.call('HGET', KEYS[1], 'family')
if redis.call('EXISTS', KEYS[1]) == 1 then
  local familyKey = ARGV[2] .. (family or KEYS[1])
  local ttl = math.max(redis.call('PTTL', KEYS[1]), redis.call('PTTL', familyKey))
  redis.call('HSET', familyKey, 'revoked', '1')
  if ttl > 0 then redis.call('PEXPIRE', familyKey, ttl) end
end
local revoked = redis.call('HGET', KEYS[1], 'revokedAt')
if revoked == '' then redis.call('HSET', KEYS[1], 'revokedAt', ARGV[1]) end
return 1
