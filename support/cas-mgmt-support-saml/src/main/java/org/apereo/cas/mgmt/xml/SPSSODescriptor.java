package org.apereo.cas.mgmt.xml;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@Data
@XmlRootElement(name = "SPSSODescriptor")
@XmlAccessorType(XmlAccessType.FIELD)
@NoArgsConstructor
public class SPSSODescriptor {

    @XmlAttribute(name = "AuthnRequestsSigned")
    private boolean authnRequestsSigned;

    @XmlAttribute(name = "WantAssertionsSigned")
    private boolean wantAssertionsSigned;

    @XmlElement(name = "NameIDFormat")
    private String nameIDFormat;

    @XmlElement(name = "AttributeConsumingService", namespace = "urn:oasis:names:tc:SAML:2.0:metadata")
    private AttributeConsumingService attributeConsumingService;

}