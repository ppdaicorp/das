/**
 * Autogenerated by Thrift Compiler (0.12.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.ppdai.das.service;


@javax.annotation.Generated(value = "Autogenerated by Thrift Compiler (0.12.0)", date = "2021-01-08")
public enum TxType implements org.apache.thrift.TEnum {
  TCC(0),
  SAGA(1);

  private final int value;

  private TxType(int value) {
    this.value = value;
  }

  /**
   * Get the integer value of this enum value, as defined in the Thrift IDL.
   */
  public int getValue() {
    return value;
  }

  /**
   * Find a the enum type by its integer value, as defined in the Thrift IDL.
   * @return null if the value is not found.
   */
  @org.apache.thrift.annotation.Nullable
  public static TxType findByValue(int value) { 
    switch (value) {
      case 0:
        return TCC;
      case 1:
        return SAGA;
      default:
        return null;
    }
  }
}
