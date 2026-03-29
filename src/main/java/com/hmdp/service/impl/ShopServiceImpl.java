package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        // 缓存穿透
        Shop shop = queryWithPassThrough(id);

        return Result.ok(shop);
     }


     public Shop queryWithPassThrough(Long id){
         //1.从缓存中查询
         String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
         //2.判断缓存是否命中
         if (StrUtil.isNotBlank(shopJson)) {
             //3.如果命中存，直接返回
             Shop shop = JSONUtil.toBean(shopJson, Shop.class);
             return shop;
         }
         // 判读是否是空值
         if (shopJson != null) { // 第一次null不会触发，后面就会触发这个，缓存压力降低
             return  null;
         }
         //4.如果未命中存，从数据库中查询
         Shop shop = getById(id);
         //5.如果数据库中不存在商铺，返回失败
         if (shop == null) {
             // 将空值写入到redis当中，防止有人不停访问不存在的id
             stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
             return null;
         }
         //6.如果数据库中存在商铺，将商铺数据缓存到Redis中
         stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
         //7.返回商铺数据
         return shop;
     }

     private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
     }

     private void unLock(String key) {
        stringRedisTemplate.delete(key);
     }

    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺的id不能为空");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);

        return Result.ok();
    }
}