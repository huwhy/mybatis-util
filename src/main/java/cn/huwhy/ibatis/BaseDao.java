package cn.huwhy.ibatis;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import cn.huwhy.interfaces.Term;

public interface BaseDao<T, PK extends Serializable> {

    Long nextId();

    void saves(@Param("list") Collection<T> list);

    void update(T po);

    void save(T po);

    void updates(@Param("list") Collection<T> pos);

    T get(PK id);

    List<T> findByTerm(Term term);

    List<T> findPaging(Term term);
}
