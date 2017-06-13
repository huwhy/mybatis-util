package cn.huwhy.ibatis;

import java.io.Serializable;
import java.util.List;

import cn.huwhy.interfaces.Term;

public interface BaseDao<T, PK extends Serializable> {

    Long nextId();

    void save(T po);

    void update(T po);

    T get(PK id);

    List<T> findByTerm(Term term);

    List<T> findPaging(Term term);
}
