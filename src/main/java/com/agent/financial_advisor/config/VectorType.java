package com.agent.financial_advisor.config;

import com.pgvector.PGvector;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

public class VectorType implements UserType<PGvector> {

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public Class<PGvector> returnedClass() {
        return PGvector.class;
    }

    @Override
    public boolean equals(PGvector x, PGvector y) {
        return x == null ? y == null : x.equals(y);
    }

    @Override
    public int hashCode(PGvector x) {
        return x == null ? 0 : x.hashCode();
    }

    @Override
    public PGvector nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner)
            throws SQLException {
        Object value = rs.getObject(position);
        if (value == null) {
            return null;
        }
        if (value instanceof PGvector) {
            return (PGvector) value;
        }
        return new PGvector(value.toString());
    }

    @Override
    public void nullSafeSet(PreparedStatement st, PGvector value, int index, SharedSessionContractImplementor session)
            throws SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            st.setObject(index, value);
        }
    }

    @Override
    public PGvector deepCopy(PGvector value) {
        if (value == null) {
            return null;
        }
        return new PGvector(value.toArray());
    }

    @Override
    public boolean isMutable() {
        return true;
    }

    @Override
    public Serializable disassemble(PGvector value) {
        return (Serializable) deepCopy(value);
    }

    @Override
    public PGvector assemble(Serializable cached, Object owner) {
        return deepCopy((PGvector) cached);
    }

    @Override
    public PGvector replace(PGvector detached, PGvector managed, Object owner) {
        return deepCopy(detached);
    }
}
