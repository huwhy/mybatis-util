package cn.huwhy.ibatis;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;

import cn.huwhy.interfaces.Term;

@Intercepts({@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})})
public class PagingInterceptor implements Interceptor {
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
        Object delegate = ReflectUtil.getFieldValue(statementHandler, "delegate");
        MappedStatement mappedStatement = (MappedStatement) ReflectUtil.getFieldValue(delegate, "mappedStatement");
        if (mappedStatement.getId().endsWith("Paging")) {
            BoundSql boundSql = (BoundSql) ReflectUtil.getFieldValue(delegate, "boundSql");
            //这里我们简单的通过传入的是Page对象就认定它是需要进行分页操作的。
            Term term = (Term) boundSql.getParameterObject();
            Connection connection = (Connection) invocation.getArgs()[0];
            //获取当前要执行的Sql语句，也就是我们直接在Mapper映射语句中写的Sql语句
            //给当前的page参数对象设置总记录数
            String sql = boundSql.getSql();
            if (term.getHasTotal()) {
                setTotalRecord(term,
                        mappedStatement, connection);
            }
            //获取分页Sql语句
            String pageSql = this.getMysqlPageSql(term, sql);
            //利用反射设置当前BoundSql对应的sql属性为我们建立好的分页Sql语句
            ReflectUtil.setFieldValue(boundSql, "sql", pageSql);
        }
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties arg0) {
        // TODO Auto-generated method stub

    }

    private void setTotalRecord(Term term,
                                MappedStatement mappedStatement, Connection connection) {
        //获取对应的BoundSql，这个BoundSql其实跟我们利用StatementHandler获取到的BoundSql是同一个对象。
        //delegate里面的boundSql也是通过mappedStatement.getBoundSql(paramObj)方法获取到的。
        BoundSql boundSql = mappedStatement.getBoundSql(term);
        //获取到我们自己写在Mapper映射语句中对应的Sql语句
        String sql = boundSql.getSql();
        //通过查询Sql语句获取到对应的计算总记录数的sql语句
        String countSql = getCountSQL(sql);
        //通过BoundSql获取对应的参数映射
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        //利用Configuration、查询记录数的Sql语句countSql、参数映射关系parameterMappings和参数对象page建立查询记录数对应的BoundSql对象。
        BoundSql countBoundSql = new BoundSql(mappedStatement.getConfiguration(), countSql, parameterMappings, term);
        //通过mappedStatement、参数对象page和BoundSql对象countBoundSql建立一个用于设定参数的ParameterHandler对象
        ParameterHandler parameterHandler = new DefaultParameterHandler(mappedStatement, term, countBoundSql);
        //通过connection建立一个countSql对应的PreparedStatement对象。
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = connection.prepareStatement(countSql);
            //通过parameterHandler给PreparedStatement对象设置参数
            parameterHandler.setParameters(pstmt);
            //之后就是执行获取总记录数的Sql语句和获取结果了。
            rs = pstmt.executeQuery();
            if (rs.next()) {
                term.setTotal(rs.getLong(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null)
                    rs.close();
                if (pstmt != null)
                    pstmt.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private String getMysqlPageSql(Term term, String sql) {
        //计算第一条记录的位置，Mysql中记录的位置是从0开始的。
        StringBuilder tempSql = new StringBuilder(sql);
        if (term.getSorts() != null && !term.getSorts().isEmpty()) {
            tempSql.append(" order by ");
            int cnt = 0;
            for (Map.Entry<String, Term.Sort> entry : term.getSorts().entrySet()) {
                if (cnt > 0) {
                    tempSql.append(", ");
                }
                tempSql.append(entry.getKey()).append(' ').append(entry.getValue());
                cnt++;
            }
        }
        if (term.getHasStart()) {
            tempSql.append(" limit ").append(term.getSize());
        } else {
            tempSql.append(" limit ").append(term.getStart()).append(",").append(term.getSize());
        }
        return tempSql.toString();
    }

    /**
     * 根据原Sql语句获取对应的查询总记录数的Sql语句
     *
     * @param sql
     * @return
     */
    public static String getCountSQL(String sql) {
        return "select count(1) from (" + sql + ") temp";
    }

}
