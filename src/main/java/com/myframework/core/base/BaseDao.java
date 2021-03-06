package com.myframework.core.base;

import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.myframework.util.IdUtil;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.support.SqlSessionDaoSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.myframework.core.entity.BaseEntity;
import com.myframework.pagehelper.Page;
import com.myframework.pagehelper.PageHelper;
import com.myframework.pagehelper.PageInfo;
import com.myframework.util.StringUtil;

/**
 * Created by zw on 2015/9/5.
 */
public abstract class BaseDao<T extends BaseEntity> extends SqlSessionDaoSupport implements IBaseDao<T> {
    private static final Logger log = LoggerFactory.getLogger(BaseController.class);

    private final String PREFIX = "I";
    private final String SUFFIX = "Dao";
    private final String QUERY_LIST = ".list";
    private final String COUNT = ".count";
    private final String GET_BY_ID = ".get";
    private final String GET_BY_NAME = ".getByName";
    private final String ADD = ".insert";
    private final String ADD_SELECTIVE = ".insertSelective";
    private final String UPDATE_BY_ID = ".update";
    private final String UPDATE_BY_ID_SELECTIVE = ".updateSelective";
    private final String DEL_BY_ID = ".delete";
    /*private final String BATCH_DEL_BY_IDS = "batchDelByIDs";
    private final String BATCH_ADD = "batchAdd";
    private final String BATCH_UPDATE = "batchUpdate";*/

    @Override
    @Autowired
    public void setSqlSessionTemplate(SqlSessionTemplate sqlSessionTemplate) {
        super.setSqlSessionTemplate(sqlSessionTemplate);
    }

    /**
     * 实体名称
     * 泛型获得XXXEntity，将其转换为XXXDao，具体操作替换掉Entity变成XXXDao，对应Mapper.xml中的namespace命名
     *
     * @return String
     */
    public String getNampSpace() {
        Class<T> clazz = (Class) ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        String appPackage = clazz.getName().substring(0, clazz.getName().indexOf(".entity"));
        String simpleName = appPackage + ".dao." + PREFIX + clazz.getSimpleName().replace("Entity", "") + SUFFIX;
        return simpleName;
    }

    @Override
    public List<T> list(Map<String, Object> params) {
        return getSqlSession().selectList(getNampSpace() + QUERY_LIST, params);
    }

    @Override
    public int count(Map<String, Object> params) {
        return (Integer) getSqlSession().selectOne(getNampSpace() + COUNT, params);
    }

    @Override
    public T get(Serializable id) {
        return getSqlSession().selectOne(getNampSpace() + GET_BY_ID, id);
    }

    @Override
    public T getByName(String name) {
        return getSqlSession().selectOne(getNampSpace() + GET_BY_NAME, name);
    }

    @Override
    public int insert(T t) {
        if (t.getId() == null) {
            Long id = generateId();
            t.setId(id);
        }
        return getSqlSession().insert(getNampSpace() + ADD, t);
    }

    @Override
    public int insertSelective(T t) {
        return getSqlSession().insert(getNampSpace() + ADD_SELECTIVE, t);
    }

    @Override
    public int update(T t) {
        return getSqlSession().update(getNampSpace() + UPDATE_BY_ID, t);
    }

    @Override
    public int updateSelective(T t) {
        return getSqlSession().update(getNampSpace() + UPDATE_BY_ID_SELECTIVE, t);
    }

    @Override
    public int delete(Serializable id) {
        return getSqlSession().delete(getNampSpace() + DEL_BY_ID, id);
    }

    @Override
    public int batchDelete(final Serializable[] ids) {
        executeBatch(new IBatchCallBack() {

            @Override
            public void doBatchCallBack() throws SQLException {
                for (Serializable id : ids) {
                    getSqlSession().delete(getNampSpace() + DEL_BY_ID, id);
                }
            }
        });
        return ids.length;
    }

    @Override
    public List<T> batchInsert(final List<T> list) {
        final List<T> results = new ArrayList<T>(list.size());
        executeBatch(new IBatchCallBack() {

            @Override
            public void doBatchCallBack() throws SQLException {
                for (T t : list) {
                    if (t.getId() == null) {
                        Long id = generateId();
                        t.setId(id);
                    }
                    getSqlSession().insert(getNampSpace() + ADD, t);
                    results.add(t);
                }
            }
        });
        return results;
    }

    @Override
    public int batchUpdate(final List<T> list) {
        executeBatch(new IBatchCallBack() {
            @Override
            public void doBatchCallBack() throws SQLException {
                for (T t : list) {
                    getSqlSession().update(getNampSpace() + UPDATE_BY_ID, t);
                }
            }
        });
        return list.size();
    }

    @Override
    public PageInfo<T> page(Map<String, Object> params, Page<T> page) {
        PageHelper.startPage(page.getPageNum(), page.getPageSize(), page.isCount());
        if (StringUtil.isNotEmpty(page.getOrderBy())) {
            PageHelper.orderBy(page.getOrderBy());
        }
        List<T> list = getSqlSession().selectList(getNampSpace() + QUERY_LIST, params);
        return new PageInfo<T>(list);
    }

    /**
     * 执行批量
     *
     * @param callBack 批量操作回调
     */
    public void executeBatch(IBatchCallBack callBack) {
        SqlSession session = ((SqlSessionTemplate) getSqlSession()).getSqlSessionFactory().openSession(ExecutorType.BATCH, false);
        try {
            callBack.doBatchCallBack();
            session.commit();
            //清理缓存，防止溢出
            session.clearCache();
        } catch (SQLException e) {
            session.rollback();
            if (log.isDebugEnabled()) {
                e.printStackTrace();
                log.debug("batchUpdate error: id [ executeBatch ], parameterObject [ callBack ].  Cause: " + e.getMessage());
            }
        } finally {
            session.close();
        }
    }

    /**
     * 获取生成的主键标识
     *
     * @return 主键标识
     */
    public Long generateId() {
        return (Long) IdUtil.getSnowflakeId();
    }
}
