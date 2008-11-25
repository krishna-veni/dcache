/**
 * ArrayOfTSURLReturnStatus.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.3 Oct 05, 2005 (05:23:37 EDT) WSDL2Java emitter.
 */

package org.dcache.srm.v2_1;

public class ArrayOfTSURLReturnStatus  implements java.io.Serializable {
    private org.dcache.srm.v2_1.TSURLReturnStatus[] surlReturnStatusArray;

    public ArrayOfTSURLReturnStatus() {
    }

    public ArrayOfTSURLReturnStatus(
           org.dcache.srm.v2_1.TSURLReturnStatus[] surlReturnStatusArray) {
           this.surlReturnStatusArray = surlReturnStatusArray;
    }


    /**
     * Gets the surlReturnStatusArray value for this ArrayOfTSURLReturnStatus.
     * 
     * @return surlReturnStatusArray
     */
    public org.dcache.srm.v2_1.TSURLReturnStatus[] getSurlReturnStatusArray() {
        return surlReturnStatusArray;
    }


    /**
     * Sets the surlReturnStatusArray value for this ArrayOfTSURLReturnStatus.
     * 
     * @param surlReturnStatusArray
     */
    public void setSurlReturnStatusArray(org.dcache.srm.v2_1.TSURLReturnStatus[] surlReturnStatusArray) {
        this.surlReturnStatusArray = surlReturnStatusArray;
    }

    public org.dcache.srm.v2_1.TSURLReturnStatus getSurlReturnStatusArray(int i) {
        return this.surlReturnStatusArray[i];
    }

    public void setSurlReturnStatusArray(int i, org.dcache.srm.v2_1.TSURLReturnStatus _value) {
        this.surlReturnStatusArray[i] = _value;
    }

    private java.lang.Object __equalsCalc = null;
    public synchronized boolean equals(java.lang.Object obj) {
        if (!(obj instanceof ArrayOfTSURLReturnStatus)) return false;
        ArrayOfTSURLReturnStatus other = (ArrayOfTSURLReturnStatus) obj;
        if (obj == null) return false;
        if (this == obj) return true;
        if (__equalsCalc != null) {
            return (__equalsCalc == obj);
        }
        __equalsCalc = obj;
        boolean _equals;
        _equals = true && 
            ((this.surlReturnStatusArray==null && other.getSurlReturnStatusArray()==null) || 
             (this.surlReturnStatusArray!=null &&
              java.util.Arrays.equals(this.surlReturnStatusArray, other.getSurlReturnStatusArray())));
        __equalsCalc = null;
        return _equals;
    }

    private boolean __hashCodeCalc = false;
    public synchronized int hashCode() {
        if (__hashCodeCalc) {
            return 0;
        }
        __hashCodeCalc = true;
        int _hashCode = 1;
        if (getSurlReturnStatusArray() != null) {
            for (int i=0;
                 i<java.lang.reflect.Array.getLength(getSurlReturnStatusArray());
                 i++) {
                java.lang.Object obj = java.lang.reflect.Array.get(getSurlReturnStatusArray(), i);
                if (obj != null &&
                    !obj.getClass().isArray()) {
                    _hashCode += obj.hashCode();
                }
            }
        }
        __hashCodeCalc = false;
        return _hashCode;
    }

    // Type metadata
    private static org.apache.axis.description.TypeDesc typeDesc =
        new org.apache.axis.description.TypeDesc(ArrayOfTSURLReturnStatus.class, true);

    static {
        typeDesc.setXmlType(new javax.xml.namespace.QName("http://srm.lbl.gov/StorageResourceManager", "ArrayOfTSURLReturnStatus"));
        org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("surlReturnStatusArray");
        elemField.setXmlName(new javax.xml.namespace.QName("", "surlReturnStatusArray"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://srm.lbl.gov/StorageResourceManager", "TSURLReturnStatus"));
        elemField.setNillable(false);
        elemField.setMaxOccursUnbounded(true);
        typeDesc.addFieldDesc(elemField);
    }

    /**
     * Return type metadata object
     */
    public static org.apache.axis.description.TypeDesc getTypeDesc() {
        return typeDesc;
    }

    /**
     * Get Custom Serializer
     */
    public static org.apache.axis.encoding.Serializer getSerializer(
           java.lang.String mechType, 
           java.lang.Class _javaType,  
           javax.xml.namespace.QName _xmlType) {
        return 
          new  org.apache.axis.encoding.ser.BeanSerializer(
            _javaType, _xmlType, typeDesc);
    }

    /**
     * Get Custom Deserializer
     */
    public static org.apache.axis.encoding.Deserializer getDeserializer(
           java.lang.String mechType, 
           java.lang.Class _javaType,  
           javax.xml.namespace.QName _xmlType) {
        return 
          new  org.apache.axis.encoding.ser.BeanDeserializer(
            _javaType, _xmlType, typeDesc);
    }

}
