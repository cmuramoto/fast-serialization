package de.ruedigermoeller.serialization;

import de.ruedigermoeller.serialization.util.FSTUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

/**
 * Copyright (c) 2012, Ruediger Moeller. All rights reserved.
 * <p/>
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * <p/>
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 * <p/>
 * Date: 02.03.14
 * Time: 13:20
 */
/**
 * Subclass optimized for "unshared mode". Cycles and Objects referenced more than once will not be detected.
 * Additionally JDK compatibility is not supported (read/writeObject and stuff). Use case is highperformance
 * serialization of plain cycle free data (e.g. messaging). Can perform significantly faster (20-40%).
 */
public class FSTObjectOutputNoShared extends FSTObjectOutput {

    /**
     * Creates a new FSTObjectOutput stream to write data to the specified
     * underlying output stream.
     * uses Default Configuration singleton
     *
     * @param out
     */
    public FSTObjectOutputNoShared(OutputStream out) {
        super(out);
    }

    /**
     * Creates a new FSTObjectOutputNoShared stream to write data to the specified
     * underlying output stream. The counter <code>written</code> is
     * set to zero.
     * Don't create a FSTConfiguration with each stream, just create one global static configuration and reuse it.
     * FSTConfiguration is threadsafe.
     *
     * @param out  the underlying output stream, to be saved for later
     *             use.
     * @param conf
     */
    public FSTObjectOutputNoShared(OutputStream out, FSTConfiguration conf) {
        super(out, conf);
        conf.setShareReferences(false);
    }

    /**
     * serialize without an underlying stream, the resulting byte array of writing to
     * this FSTObjectOutput can be accessed using getBuffer(), the size using getWritten().
     * <p/>
     * Don't create a FSTConfiguration with each stream, just create one global static configuration and reuseit.
     * FSTConfiguration is threadsafe.
     *
     * @param conf
     */
    public FSTObjectOutputNoShared(FSTConfiguration conf) {
        super(conf);
        conf.setShareReferences(false);
    }

    /**
     * serialize without an underlying stream, the resulting byte array of writing to
     * this FSTObjectOutput can be accessed using getBuffer(), the size using getWritten().
     * <p/>
     * uses default configuration singleton
     *
     */
    public FSTObjectOutputNoShared() {
        super();
    }

    protected void writeObjectHeader(FSTClazzInfo clsInfo, FSTClazzInfo.FSTFieldInfo referencee, Object toWrite) throws IOException {
        if ( toWrite.getClass() == referencee.getType() )
        {
            writeFByte(TYPED);
        } else {
            final Class[] possibleClasses = referencee.getPossibleClasses();
            if ( possibleClasses == null ) {
                writeFByte(OBJECT);
                writeClass(clsInfo);
            } else {
                final int length = possibleClasses.length;
                for (int j = 0; j < length; j++) {
                    final Class possibleClass = possibleClasses[j];
                    if ( possibleClass == toWrite.getClass() ) {
                        writeFByte(j+1);
                        return;
                    }
                }
                writeFByte(OBJECT);
                writeClass(clsInfo);
            }
        }
    }

    @Override
    protected void writeObjectWithContext(FSTClazzInfo.FSTFieldInfo referencee, Object toWrite) throws IOException {
        if ( toWrite == null ) {
            writeFByte(NULL);
            return;
        }
        final Class clazz = toWrite.getClass();
        if ( clazz == String.class ) {
            String[] oneOf = referencee.getOneOf();
            if ( oneOf != null ) {
                for (int i = 0; i < oneOf.length; i++) {
                    String s = oneOf[i];
                    if ( s.equals(toWrite) ) {
                        writeFByte(ONE_OF);
                        writeFByte(i);
                        return;
                    }
                }
            }
            writeFByte(STRING);
            writeStringUTF((String) toWrite);
            return;
        } else if ( clazz == Integer.class ) { writeFByte(BIG_INT); writeCInt(((Integer) toWrite).intValue()); return;
        } else if ( clazz == Long.class ) { writeFByte(BIG_LONG); writeCLong(((Long) toWrite).longValue()); return;
        } else if ( clazz == Boolean.class ) { writeFByte(((Boolean) toWrite).booleanValue() ? BIG_BOOLEAN_TRUE : BIG_BOOLEAN_FALSE); return;
        } else if ( clazz.isArray() ) {
            writeFByte(ARRAY);
            writeArray(referencee, toWrite);
            return;
        } else if ( (referencee.getType() != null && referencee.getType().isEnum()) || toWrite instanceof Enum ) {
            writeFByte(ENUM);
            Class c = toWrite.getClass();
            boolean isEnumClass = c.isEnum();
            if ( ! isEnumClass ) {
                // weird stuff ..
                while ( c != null && ! c.isEnum() ) {
                    c = toWrite.getClass().getEnclosingClass();
                }
                if ( c == null ) {
                    throw new RuntimeException("Can't handle this enum: "+toWrite.getClass());
                }
                writeClass(c);
            } else {
                writeClass(getFstClazzInfo(referencee,toWrite.getClass()));
            }
            writeCInt(((Enum) toWrite).ordinal());
            return;
        }

        FSTClazzInfo serializationInfo = getFstClazzInfo(referencee, clazz);
        FSTObjectSerializer ser = serializationInfo.getSer();
        // Object header (nothing written till here)
        writeObjectHeader(serializationInfo, referencee, toWrite);
        if ( ser == null ) {
            defaultWriteObject(toWrite, serializationInfo);
        } else {
            // write object depending on type (custom, externalizable, serializable/java, default)
            ser.writeObject(this, toWrite, serializationInfo, referencee, written);
        }
    }

    public void resetForReUse( OutputStream out ) {
        if ( closed )
            throw new RuntimeException("Can't reuse closed stream");
        reset();
        if ( out != null ) {
            buffout.setOutstream(out);
        } else {
            this.out = buffout;
        }
        clnames.clear();
    }

    public void resetForReUse( byte[] out ) {
        if ( closed )
            throw new RuntimeException("Can't reuse closed stream");
        reset();
        this.out = buffout;
        buffout.reset(out);
        clnames.clear();
    }

}
