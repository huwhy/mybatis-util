package cn.huwhy.ibatis;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import cn.huwhy.interfaces.Term;

public interface BaseDao<T, PK extends Serializable> {

    Long nextId();

    int saves(@Param("list") Collection<T> list);

    int update(T po);

    int save(T po);

    int updates(@Param("list") Collection<T> pos);

    T get(PK id);

    List<T> findByTerm(Term term);

    List<T> findPaging(Term term);
}
