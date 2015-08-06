// Copyright (c) 2015, Cloudera, inc.
// Confidential Cloudera Information: Covered by NDA.
package org.kududb.client;

import java.util.BitSet;
import java.util.List;

import com.google.common.collect.Lists;
import com.google.common.primitives.Longs;
import org.kududb.ColumnSchema;
import org.kududb.Schema;
import org.kududb.Type;

/**
 * Class used to represent parts of row along with its schema.
 *
 * Each PartialRow is backed by an byte array where all the cells (except strings) are written. The
 * strings are kept in a List.
 */
public class PartialRow {

  private final Schema schema;
  // String data, post UTF8-encoding.
  private final List<byte[]> strings;
  private final int rowSize;
  private final byte[] rowAlloc;
  private final BitSet columnsBitSet;
  private final BitSet nullsBitSet;

  /**
   * Package-private constructor, use {@link KuduTable#newPartialRow()} to get a new partial row.
   * @param table table with the schema to use for this row
   */
  PartialRow(KuduTable table) {
    this.schema = table.getSchema();
    this.columnsBitSet = new BitSet(this.schema.getColumnCount());
    this.nullsBitSet = schema.hasNullableColumns() ?
        new BitSet(this.schema.getColumnCount()) : null;
    this.rowSize = schema.getRowSize();
    this.rowAlloc = new byte[this.rowSize];
    strings = Lists.newArrayListWithCapacity(schema.getStringCount());
  }

  /**
   * Add a boolean for the specified column.
   * @param columnIndex the column's index in the schema
   * @param val value to add
   * @throws IllegalArgumentException if the column doesn't exist or the value doesn't match
   * the column's type
   */
  public void addBoolean(int columnIndex, boolean val) {
    addBoolean(this.schema.getColumn(columnIndex), val);
  }

  /**
   * Add a boolean for the specified column.
   * @param columnName Name of the column
   * @param val value to add
   * @throws IllegalArgumentException if the column doesn't exist or the value doesn't match
   * the column's type
   */
  public void addBoolean(String columnName, boolean val) {
    addBoolean(this.schema.getColumn(columnName), val);
  }

  private void addBoolean(ColumnSchema column, boolean val) {
    checkColumn(column, Type.BOOL);
    rowAlloc[getPositionInRowAllocAndSetBitSet(column)] = (byte) (val ? 1 : 0);
  }

  /**
   * Add a byte for the specified column.
   * @param columnIndex the column's index in the schema
   * @param val value to add
   * @throws IllegalArgumentException if the column doesn't exist or the value doesn't match
   * the column's type
   */
  public void addByte(int columnIndex, byte val) {
    addByte(this.schema.getColumn(columnIndex), val);
  }

  /**
   * Add a byte for the specified column.
   * @param columnName Name of the column
   * @param val value to add
   * @throws IllegalArgumentException if the column doesn't exist or the value doesn't match
   * the column's type
   */
  public void addByte(String columnName, byte val) {
    addByte(this.schema.getColumn(columnName), val);
  }

  void addByte(ColumnSchema column, byte val) {
    checkColumn(column, Type.INT8);
    rowAlloc[getPositionInRowAllocAndSetBitSet(column)] = val;
  }

  /**
   * Add a short for the specified column.
   * @param columnIndex the column's index in the schema
   * @param val value to add
   * @throws IllegalArgumentException if the column doesn't exist or the value doesn't match
   * the column's type
   */
  public void addShort(int columnIndex, short val) {
    addShort(this.schema.getColumn(columnIndex), val);
  }

  /**
   * Add a short for the specified column.
   * @param columnName Name of the column
   * @param val value to add
   * @throws IllegalArgumentException if the column doesn't exist or the value doesn't match
   * the column's type
   */
  public void addShort(String columnName, short val) {
    addShort(this.schema.getColumn(columnName), val);
  }

  private void addShort(ColumnSchema column, short val) {
    checkColumn(column, Type.INT16);
    Bytes.setShort(rowAlloc, val, getPositionInRowAllocAndSetBitSet(column));
  }

  /**
   * Add an int for the specified column.
   * @param columnIndex the column's index in the schema
   * @param val value to add
   * @throws IllegalArgumentException if the column doesn't exist or the value doesn't match
   * the column's type
   */
  public void addInt(int columnIndex, int val) {
    addInt(this.schema.getColumn(columnIndex), val);
  }

  /**
   * Add an int for the specified column.
   * @param columnName Name of the column
   * @param val value to add
   * @throws IllegalArgumentException if the column doesn't exist or the value doesn't match
   * the column's type
   */
  public void addInt(String columnName, int val) {
    addInt(this.schema.getColumn(columnName), val);
  }

  private void addInt(ColumnSchema column, int val) {
    checkColumn(column, Type.INT32);
    Bytes.setInt(rowAlloc, val, getPositionInRowAllocAndSetBitSet(column));
  }

  /**
   * Add an long for the specified column.
   * @param columnIndex the column's index in the schema
   * @param val value to add
   * @throws IllegalArgumentException if the column doesn't exist or the value doesn't match
   * the column's type
   */
  public void addLong(int columnIndex, long val) {
    addLong(this.schema.getColumn(columnIndex), val);
  }

  /**
   * Add an long for the specified column.
   * @param columnName Name of the column
   * @param val value to add
   * @throws IllegalArgumentException if the column doesn't exist or the value doesn't match
   * the column's type
   */
  public void addLong(String columnName, long val) {
    addLong(this.schema.getColumn(columnName), val);
  }

  private void addLong(ColumnSchema column, long val) {
    checkColumn(column, Type.INT64);
    Bytes.setLong(rowAlloc, val, getPositionInRowAllocAndSetBitSet(column));
  }

  /**
   * Add an float for the specified column.
   * @param columnIndex the column's index in the schema
   * @param val value to add
   * @throws IllegalArgumentException if the column doesn't exist or the value doesn't match
   * the column's type
   */
  public void addFloat(int columnIndex, float val) {
    addFloat(this.schema.getColumn(columnIndex), val);
  }

  /**
   * Add an float for the specified column.
   * @param columnName Name of the column
   * @param val value to add
   * @throws IllegalArgumentException if the column doesn't exist or the value doesn't match
   * the column's type
   */
  public void addFloat(String columnName, float val) {
    addFloat(this.schema.getColumn(columnName), val);
  }

  private void addFloat(ColumnSchema column, float val) {
    checkColumn(column, Type.FLOAT);
    Bytes.setFloat(rowAlloc, val, getPositionInRowAllocAndSetBitSet(column));
  }

  /**
   * Add an double for the specified column.
   * @param columnIndex the column's index in the schema
   * @param val value to add
   * @throws IllegalArgumentException if the column doesn't exist or the value doesn't match
   * the column's type
   */
  public void addDouble(int columnIndex, double val) {
    addDouble(this.schema.getColumn(columnIndex), val);
  }

  /**
   * Add an double for the specified column.
   * @param columnName Name of the column
   * @param val value to add
   * @throws IllegalArgumentException if the column doesn't exist or the value doesn't match
   * the column's type
   */
  public void addDouble(String columnName, double val) {
    addDouble(this.schema.getColumn(columnName), val);
  }

  private void addDouble(ColumnSchema column, double val) {
    checkColumn(column, Type.DOUBLE);
    Bytes.setDouble(rowAlloc, val, getPositionInRowAllocAndSetBitSet(column));
  }

  /**
   * Add a String for the specified column.
   * @param columnIndex the column's index in the schema
   * @param val value to add
   * @throws IllegalArgumentException if the column doesn't exist or the value doesn't match
   * the column's type
   */
  public void addString(int columnIndex, String val) {
    addStringUtf8(columnIndex, Bytes.fromString(val));
  }

  /**
   * Add a String for the specified column.
   * @param columnName Name of the column
   * @param val value to add
   * @throws IllegalArgumentException if the column doesn't exist or the value doesn't match
   * the column's type
   */
  public void addString(String columnName, String val) {
    addStringUtf8(columnName, Bytes.fromString(val));
  }

  /**
   * Add a String for the specified value, encoded as UTF8.
   * Note that the provided value must not be mutated after this.
   * @param columnIndex the column's index in the schema
   * @param val value to add
   * @throws IllegalArgumentException if the column doesn't exist or the value doesn't match
   * the column's type
   */
  public void addStringUtf8(int columnIndex, byte[] val) {
    addStringUtf8(this.schema.getColumn(columnIndex), val);
  }

  /**
   * Add a String for the specified value, encoded as UTF8.
   * Note that the provided value must not be mutated after this.
   * @param columnName Name of the column
   * @param val value to add
   * @throws IllegalArgumentException if the column doesn't exist or the value doesn't match
   * the column's type
   */
  public void addStringUtf8(String columnName, byte[] val) {
    addStringUtf8(this.schema.getColumn(columnName), val);
  }

  /**
   * Add a String for the specified value, encoded as UTF8.
   * Note that the provided value must not be mutated after this.
   */
  public void addStringUtf8(ColumnSchema column, byte[] val) {
    // TODO: use Utf8.isWellFormed from Guava 16 to verify that
    // the user isn't putting in any garbage data.
    checkColumn(column, Type.STRING);
    int stringsIdx = strings.size();
    strings.add(val);
    // Set the bit and set the usage bit
    int pos = getPositionInRowAllocAndSetBitSet(column);

    // For now, just store the index of the string.
    // Later, we'll replace this with the offset within the wire buffer
    // before we send it.
    // TODO We don't need to write the length, we could do one of the following:
    // - Remove the string lengths from rowAlloc, or
    // - Modify Operation to single copy rowAlloc instead of copying column by column.
    Bytes.setLong(rowAlloc, stringsIdx, pos);
    Bytes.setLong(rowAlloc, val.length, pos + Longs.BYTES);
  }

  /**
   * Set the specified column to null
   * @param columnIndex the column's index in the schema
   * @throws IllegalArgumentException if the column doesn't exist or cannot be set to null
   */
  public void setNull(int columnIndex) {
    setNull(this.schema.getColumn(columnIndex));
  }

  /**
   * Set the specified column to null
   * @param columnName Name of the column
   * @throws IllegalArgumentException if the column doesn't exist or cannot be set to null
   */
  public void setNull(String columnName) {
    setNull(this.schema.getColumn(columnName));
  }

  private void setNull(ColumnSchema column) {
    assert nullsBitSet != null;
    checkColumnExists(column);
    if (!column.isNullable()) {
      throw new IllegalArgumentException(column.getName() + " cannot be set to null");
    }
    int idx = schema.getColumns().indexOf(column);
    columnsBitSet.set(idx);
    nullsBitSet.set(idx);
  }

  /**
   * Verifies if the column exists and belongs to the specified type
   * It also does some internal accounting
   * @param column column the user wants to set
   * @param type type we expect
   * @throws IllegalArgumentException if the column or type was invalid
   */
  private void checkColumn(ColumnSchema column, Type type) {
    checkColumnExists(column);
    if (!column.getType().equals(type))
      throw new IllegalArgumentException(column.getName() +
          " isn't " + type.getName() + ", it's " + column.getType().getName());
  }

  /**
   * @param column column the user wants to set
   * @throws IllegalArgumentException if the column doesn't exist
   */
  private void checkColumnExists(ColumnSchema column) {
    if (column == null)
      throw new IllegalArgumentException("Column name isn't present in the table's schema");
  }

  /**
   * Gives the column's location in the byte array and marks it as set
   * @param column column to get the position for and mark as set
   * @return the offset in rowAlloc for the column
   */
  private int getPositionInRowAllocAndSetBitSet(ColumnSchema column) {
    int idx = schema.getColumns().indexOf(column);
    columnsBitSet.set(idx);
    return schema.getColumnOffset(idx);
  }

  /**
   * Tells if the specified column was set by the user
   * @param column column's index in the schema
   * @return true if it was set, else false
   */
  boolean isSet(int column) {
    return this.columnsBitSet.get(column);
  }

  /**
   * Tells if the specified column was set to null by the user
   * @param column column's index in the schema
   * @return true if it was set, else false
   */
  boolean isSetToNull(int column) {
    if (this.nullsBitSet == null) {
      return false;
    }
    return this.nullsBitSet.get(column);
  }

  /**
   * Encodes the row key based on the set columns and returns it.
   * @return a byte array containing an encoded row key
   */
  public byte[] key() {
    int seenStrings = 0;
    KeyEncoder keyEncoder = new KeyEncoder(this.schema);
    for (int i = 0; i < this.schema.getKeysCount(); i++) {
      ColumnSchema column = this.schema.getColumn(i);
      if (column.getType() == Type.STRING) {
        byte[] string = this.strings.get(seenStrings);
        seenStrings++;
        keyEncoder.addKey(string, 0, string.length, column, i);
      } else {
        keyEncoder.addKey(this.rowAlloc, this.schema.getColumnOffset(i), column.getType().getSize(),
            column, i);
      }
    }
    // TODO we might want to cache the key
    return keyEncoder.extractByteArray();
  }

  /**
   * Get the schema used for this row.
   * @return a schema that came from KuduTable
   */
  Schema getSchema() {
    return schema;
  }

  /**
   * Get the list of UTF-8 encoded Strings that were added to this row.
   * @return a list of strings, may be empty
   */
  List<byte[]> getStrings() {
    return strings;
  }

  /**
   * Get the byte array that contains all the data added to this partial row. Strings are contained
   * separately, see {@link #getStrings()}. In their place you'll find their index in that list and
   * their size.
   * @return a byte array containing the data for this row, except strings
   */
  byte[] getRowAlloc() {
    return rowAlloc;
  }

  /**
   * Get the bit set that indicates which columns were set.
   * @return a bit set for columns with data
   */
  BitSet getColumnsBitSet() {
    return columnsBitSet;
  }

  /**
   * Get the bit set for the columns that were specifically set to null
   * @return a bit set for null columns
   */
  BitSet getNullsBitSet() {
    return nullsBitSet;
  }

}