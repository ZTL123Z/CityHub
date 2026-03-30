package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    
    @Resource 
    private ISeckillVoucherService seckillVoucherService;
    
    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    private static final ExecutorService SEKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init(){
        SEKILL_ORDER_EXECUTOR.submit(new VoucherOrderHander());
    }

    private class VoucherOrderHander implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    // 1.获取订单
                    VoucherOrder order = orderTasks.take();
                    // 2.创建订单
                    handleVoucherOrder(order);
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }
                finally {
                    orderTasks.remove();
                }
            }
        }

        private void handleVoucherOrder(VoucherOrder voucherOrder) {
            // 1.获得用户
            Long userId = voucherOrder.getUserId();
            // 6.给userId加锁
            // 创建锁对象
            SimpleRedisLock lock = new SimpleRedisLock(userId.toString(), stringRedisTemplate);
            // 7.获取锁
            if (!lock.tryLock(5)) {
               log.error("不允许重复下单");
                return;
            }
            try {
                // 8.创建订单
                proxy.createVoucherOrder(voucherOrder);
            } finally {
                // 9.释放锁
                lock.unlock();
            }
        }
    }

    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        //1.执行lua脚本
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString());
        // 2.判断结果是否为0
        int r = result.intValue();
        if(r != 0) {
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }

        long orderId = redisIdWorker.nextId("voucher_order");
        // 3.返回订单ID
        return Result.ok(orderId);
    }



//    @Override
//    @Transactional
//    public Result seckillVoucher(Long voucherId) {
//        // 1.查询优惠劵
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//
//        // 2.秒杀是否开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀还没开始");
//        }
//        // 3.秒杀是否结束
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已经结束");
//        }
//        // 4.库存是否充足
//        if (voucher.getStock() < 1) {
//            return Result.fail("库存不足");
//        }
//        // 5.扣减库存
//        boolean success = seckillVoucherService.update()
//                .setSql("stock = stock - 1")
//                .eq("voucher_id", voucherId).gt("stock", 0) //加上乐观锁
//                .update();
//        if (!success) {
//            return Result.fail("库存不足");
//        }
//        Long userId = UserHolder.getUser().getId();
//        // 6.给userId加锁
//        // 创建锁对象
//        SimpleRedisLock lock = new SimpleRedisLock(userId.toString(), stringRedisTemplate);
//        // 7.获取锁
//        if (!lock.tryLock(5)) {
//            return Result.fail("请稍后再试");
//        }
//        try {
//            // 8.创建订单
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            // 9.释放锁
//            lock.unlock();
//        }
//
//    }

    private IVoucherOrderService proxy;

    @Override
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //5.一人一单
        Long userId = voucherOrder.getUserId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder).count();
        if (count > 0) {

            return;
        }
        // 6.创建订单;
        //6.3代金券id
        save(voucherOrder);

        orderTasks.add(voucherOrder);
        // 获得代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
    }
}
