/**
 * SrmStatusOfPutRequestRequest.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.3 Oct 05, 2005 (05:23:37 EDT) WSDL2Java emitter.
 */

package org.dcache.srm.v2_1;

public class SrmStatusOfPutRequestRequest  implements java.io.Serializable {
    private org.dcache.srm.v2_1.TRequestToken requestToken;

    private org.dcache.srm.v2_1.TUserID userID;

    private org.dcache.srm.v2_1.ArrayOfTSURL arrayOfToSURLs;

    public SrmStatusOfPutRequestRequest() {
    }

    public SrmStatusOfPutRequestRequest(
           org.dcache.srm.v2_1.TRequestToken requestToken,
           org.dcache.srm.v2_1.TUserID userID,
           org.dcache.srm.v2_1.ArrayOfTSURL arrayOfToSURLs) {
           this.requestToken = requestToken;
           this.userID = userID;
           this.arrayOfToSURLs = arrayOfToSURLs;
    }


    /**
     * Gets the requestToken value for this SrmStatusOfPutRequestRequest.
     * 
     * @return requestToken
     */
    public org.dcache.srm.v2_1.TRequestToken getRequestToken() {
        return requestToken;
    }


    /**
     * Sets the requestToken value for this SrmStatusOfPutRequestRequest.
     * 
     * @param requestToken
     */
    public void setRequestToken(org.dcache.srm.v2_1.TRequestToken requestToken) {
        this.requestToken = requestToken;
    }


    /**
     * Gets the userID value for this SrmStatusOfPutRequestRequest.
     * 
     * @return userID
     */
    public org.dcache.srm.v2_1.TUserID getUserID() {
        return userID;
    }


    /**
     * Sets the userID value for this SrmStatusOfPutRequestRequest.
     * 
     * @param userID
     */
    public void setUserID(org.dcache.srm.v2_1.TUserID userID) {
        this.userID = userID;
    }


    /**
     * Gets the arrayOfToSURLs value for this SrmStatusOfPutRequestRequest.
     * 
     * @return arrayOfToSURLs
     */
    public org.dcache.srm.v2_1.ArrayOfTSURL getArrayOfToSURLs() {
        return arrayOfToSURLs;
    }


    /**
     * Sets the arrayOfToSURLs value for this SrmStatusOfPutRequestRequest.
     * 
     * @param arrayOfToSURLs
     */
    public void setArrayOfToSURLs(org.dcache.srm.v2_1.ArrayOfTSURL arrayOfToSURLs) {
        this.arrayOfToSURLs = arrayOfToSURLs;
    }

    private java.lang.Object __equalsCalc = null;
    public synchronized boolean equals(java.lang.Object obj) {
        if (!(obj instanceof SrmStatusOfPutRequestRequest)) return false;
        SrmStatusOfPutRequestRequest other = (SrmStatusOfPutRequestRequest) obj;
        if (obj == null) return false;
        if (this == obj) return true;
        if (__equalsCalc != null) {
            return (__equalsCalc == obj);
        }
        __equalsCalc = obj;
        boolean _equals;
        _equals = true && 
            ((this.requestToken==null && other.getRequestToken()==null) || 
             (this.requestToken!=null &&
              this.requestToken.equals(other.getRequestToken()))) &&
            ((this.userID==null && other.getUserID()==null) || 
             (this.userID!=null &&
              this.userID.equals(other.getUserID()))) &&
            ((this.arrayOfToSURLs==null && other.getArrayOfToSURLs()==null) || 
             (this.arrayOfToSURLs!=null &&
              this.arrayOfToSURLs.equals(other.getArrayOfToSURLs())));
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
        if (getRequestToken() != null) {
            _hashCode += getRequestToken().hashCode();
        }
        if (getUserID() != null) {
            _hashCode += getUserID().hashCode();
        }
        if (getArrayOfToSURLs() != null) {
            _hashCode += getArrayOfToSURLs().hashCode();
        }
        __hashCodeCalc = false;
        return _hashCode;
    }

    // Type metadata
    private static org.apache.axis.description.TypeDesc typeDesc =
        new org.apache.axis.description.TypeDesc(SrmStatusOfPutRequestRequest.class, true);

    static {
        typeDesc.setXmlType(new javax.xml.namespace.QName("http://srm.lbl.gov/StorageResourceManager", "srmStatusOfPutRequestRequest"));
        org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("requestToken");
        elemField.setXmlName(new javax.xml.namespace.QName("", "requestToken"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://srm.lbl.gov/StorageResourceManager", "TRequestToken"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("userID");
        elemField.setXmlName(new javax.xml.namespace.QName("", "userID"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://srm.lbl.gov/StorageResourceManager", "TUserID"));
        elemField.setMinOccurs(0);
        elemField.setNillable(true);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("arrayOfToSURLs");
        elemField.setXmlName(new javax.xml.namespace.QName("", "arrayOfToSURLs"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://srm.lbl.gov/StorageResourceManager", "ArrayOfTSURL"));
        elemField.setMinOccurs(0);
        elemField.setNillable(true);
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
