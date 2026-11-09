redis.call('INCR', KEYS[1])
redis.call('PERSIST', KEYS[1])
return 1
