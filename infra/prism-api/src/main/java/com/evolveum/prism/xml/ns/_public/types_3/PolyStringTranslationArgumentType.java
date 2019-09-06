/*
 * Copyright (c) 2019 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.4
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a>
// Any modifications to this file will be lost upon recompilation of the source schema.
// Generated on: 2014.02.04 at 01:34:24 PM CET
//


package com.evolveum.prism.xml.ns._public.types_3;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.namespace.QName;


/**
 * WARNING: this is NOT a generated code.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PolyStringTranslationArgumentType", propOrder = {
    "value",
    "translation"
})
public class PolyStringTranslationArgumentType implements Serializable, Cloneable {
	private static final long serialVersionUID = 1L;

	public static final QName COMPLEX_TYPE = new QName("http://prism.evolveum.com/xml/ns/public/types-3", "PolyStringTranslationArgumentType");

    protected String value;
    protected PolyStringTranslationType translation;

	public PolyStringTranslationArgumentType() {
	}

	public PolyStringTranslationArgumentType(PolyStringTranslationType translation) {
		this.translation = translation;
	}

	public PolyStringTranslationArgumentType(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public PolyStringTranslationType getTranslation() {
		return translation;
	}

	public void setTranslation(PolyStringTranslationType translation) {
		this.translation = translation;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((translation == null) ? 0 : translation.hashCode());
		result = prime * result + ((value == null) ? 0 : value.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PolyStringTranslationArgumentType other = (PolyStringTranslationArgumentType) obj;
		if (translation == null) {
			if (other.translation != null)
				return false;
		} else if (!translation.equals(other.translation))
			return false;
		if (value == null) {
			if (other.value != null)
				return false;
		} else if (!value.equals(other.value))
			return false;
		return true;
	}

    @Override
	public String toString() {
		return "PolyStringTranslationArgumentType(value=" + value + ", translation=" + translation + ")";
	}

	@Override
    public PolyStringTranslationArgumentType clone() {
        PolyStringTranslationArgumentType cloned = new PolyStringTranslationArgumentType();
        cloned.setValue(value);
        if (translation != null) {
        	cloned.setTranslation(translation.clone());
        }
        return cloned;
    }
}