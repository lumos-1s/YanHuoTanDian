--1.参数列表
--1.1优惠券id
local voucherId = ARGV[1]
--1.2用户id
local userId = ARGV[2]
--1.3订单id
local orderId = ARGV[3]

--2.数据key
--2.1库存key
local stockKey = 'seckill:stock:' .. voucherId
--2.2订单key
local orderKey = 'seckill:order:' .. voucherId

--3.脚本业务
--3.1判断库存是否大于0
if (tonumber(redis.call('get', stockKey)) <= 0) then

--3.2库存为0，返回1
    return 1
end
--3.3判断用户是否下单
if (redis.call('sismember', orderKey, userId) == 1) then
    --3.4如果存在，是重复下单，返回2
    return 2
end
--扣库存
--3.5下单，将用户id存入set集合
redis.call('incrby', stockKey, -1)
redis.call('sadd', orderKey, userId)
--3.6发送消息到队列中 XADD stream.orders ...
redis.call('xadd', 'stream.orders','*', 'voucherId', voucherId, 'userId', userId, 'id', orderId)

return 0