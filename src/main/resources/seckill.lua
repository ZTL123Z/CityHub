-- 1.参数列表
-- 1.1 优惠卷id
local voucherId = ARGV[1]
-- 1.2 用户id
local userId = ARGV[2]
-- 2.数据key
local stockKey = "seckill:stock:" .. voucherId
local orderKey = "seckill:order:" .. voucherId
-- 3.判断库存是否充足
if(tonumber(redis.call('get', stockKey)) <= 0) then
	return 1
end
-- 4.判断用户是否已购买
if(redis.call('sismember', orderKey, userId) >= 1) then
	return 2
end
-- 5.扣库存
redis.call('incrby', stockKey, -1)
-- 6.添加订单
redis.call('sadd', orderKey, userId)
-- 7.返回成功
return 0



