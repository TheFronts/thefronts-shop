package cn.thefronts.common.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.ExceptionUtils;
import com.baomidou.mybatisplus.core.toolkit.ReflectionKit;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.thefronts.common.exception.IdempotencyException;
import cn.thefronts.common.exception.LockException;
import cn.thefronts.common.lock.DistributedLock;
import cn.thefronts.common.lock.ZLock;
import cn.thefronts.common.service.ISuperService;

import java.io.Serializable;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * service实现父类
 *
 * @author chenkun
 * @date 2023/2/22 16:44
 */
public class SuperServiceImpl<M extends BaseMapper<T>, T> extends ServiceImpl<M, T> implements ISuperService<T> {
    @Override
    public boolean saveIdempotency(T entity, DistributedLock locker, String lockKey, Wrapper<T> countWrapper, String msg) throws Exception {
        if (locker == null) {
            throw new LockException("DistributedLock is null");
        }
        if (StrUtil.isEmpty(lockKey)) {
            throw new LockException("lockKey is null");
        }
        try (
                ZLock lock = locker.tryLock(lockKey, 10, 60, TimeUnit.SECONDS);
        ) {
            if (lock != null) {
                //判断记录是否已存在
                long count = super.count(countWrapper);
                if (count == 0) {
                    return super.save(entity);
                } else {
                    if (StrUtil.isEmpty(msg)) {
                        msg = "已存在";
                    }
                    throw new IdempotencyException(msg);
                }
            } else {
                throw new LockException("锁等待超时");
            }
        }
    }

    @Override
    public boolean saveIdempotency(T entity, DistributedLock lock, String lockKey, Wrapper<T> countWrapper) throws Exception {
        return saveIdempotency(entity, lock, lockKey, countWrapper, null);
    }

    @Override
    public boolean saveOrUpdateIdempotency(T entity, DistributedLock lock, String lockKey, Wrapper<T> countWrapper, String msg) throws Exception {
        if (null != entity) {
            Class<?> cls = entity.getClass();
            TableInfo tableInfo = TableInfoHelper.getTableInfo(cls);
            if (null != tableInfo && StrUtil.isNotEmpty(tableInfo.getKeyProperty())) {
                Object idVal = ReflectionKit.getFieldValue(entity, tableInfo.getKeyProperty());
                if (StringUtils.checkValNull(idVal) || Objects.isNull(getById((Serializable) idVal))) {
                    if (StrUtil.isEmpty(msg)) {
                        msg = "已存在";
                    }
                    return this.saveIdempotency(entity, lock, lockKey, countWrapper, msg);
                } else {
                    return updateById(entity);
                }
            } else {
                throw ExceptionUtils.mpe("Error:  Can not execute. Could not find @TableId.");
            }
        }
        return false;
    }

    @Override
    public boolean saveOrUpdateIdempotency(T entity, DistributedLock lock, String lockKey, Wrapper<T> countWrapper) throws Exception {
        return this.saveOrUpdateIdempotency(entity, lock, lockKey, countWrapper, null);
    }
}
