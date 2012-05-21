/*
 * Copyright (c) 2012 Evolveum
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1 or
 * CDDLv1.0.txt file in the source code distribution.
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 *
 * Portions Copyrighted 2012 [name of copyright owner]
 */

package com.evolveum.midpoint.repo.sql.data.common;

import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.repo.sql.DtoTranslationException;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ExclusionPolicyType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ExclusionType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectType;
import org.apache.commons.lang.Validate;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.Type;

import javax.persistence.*;

/**
 * @author lazyman
 */
@Entity
@Table(name = "m_exclusion")
@ForeignKey(name = "fk_exclusion")
public class RExclusion extends RContainer implements ROwnable  {

    //owner
    private RObject owner;
    private String ownerOid;
    private Long ownerId;
    //exclusion
    private String description;
    private RObjectReference targetRef;
    private ExclusionPolicyType policy;

    @ForeignKey(name = "fk_exclusion_owner")
    @MapsId("owner")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "owner_oid", referencedColumnName = "oid"),
            @JoinColumn(name = "owner_id", referencedColumnName = "id")
    })
    public RObject getOwner() {
        return owner;
    }

    @Column(name = "owner_id", nullable = false)
    public Long getOwnerId() {
        if (ownerId == null && owner != null) {
            ownerId = owner.getId();
        }
        return ownerId;
    }

    @Column(name = "owner_oid", length = 36, nullable = false)
    public String getOwnerOid() {
        if (ownerOid == null && owner != null) {
            ownerOid = owner.getOid();
        }
        return ownerOid;
    }

    @Type(type = "org.hibernate.type.TextType")
    public String getDescription() {
        return description;
    }

    @Enumerated(EnumType.ORDINAL)
    public ExclusionPolicyType getPolicy() {
        return policy;
    }

    @OneToOne(optional = true, mappedBy = "owner", orphanRemoval = true)
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    public RObjectReference getTargetRef() {
        return targetRef;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setPolicy(ExclusionPolicyType policy) {
        this.policy = policy;
    }

    public void setTargetRef(RObjectReference targetRef) {
        this.targetRef = targetRef;
    }

    public void setOwner(RObject owner) {
        this.owner = owner;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public void setOwnerOid(String ownerOid) {
        this.ownerOid = ownerOid;
    }

    @Transient
    @Override
    public RContainer getContainerOwner() {
        return getOwner();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        RExclusion that = (RExclusion) o;

        if (description != null ? !description.equals(that.description) : that.description != null) return false;
        if (policy != that.policy) return false;
        if (targetRef != null ? !targetRef.equals(that.targetRef) : that.targetRef != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (policy != null ? policy.hashCode() : 0);
        return result;
    }

    public static void copyToJAXB(RExclusion repo, ExclusionType jaxb, PrismContext prismContext) throws
            DtoTranslationException {
        Validate.notNull(repo, "Repo object must not be null.");
        Validate.notNull(jaxb, "JAXB object must not be null.");

        jaxb.setDescription(repo.getDescription());
        jaxb.setId(RUtil.getStringFromLong(repo.getId()));
        jaxb.setPolicy(repo.getPolicy());

        if (repo.getTargetRef() != null) {
            jaxb.setTargetRef(repo.getTargetRef().toJAXB(prismContext));
        }
    }

    public static void copyFromJAXB(ExclusionType jaxb, RExclusion repo, ObjectType parent, PrismContext prismContext)
            throws DtoTranslationException {
        Validate.notNull(repo, "Repo object must not be null.");
        Validate.notNull(jaxb, "JAXB object must not be null.");

        repo.setOid(parent.getOid());
        repo.setId(RUtil.getLongWrappedFromString(jaxb.getId()));

        repo.setDescription(jaxb.getDescription());
        repo.setPolicy(jaxb.getPolicy());
        repo.setTargetRef(RUtil.jaxbRefToRepo(jaxb.getTargetRef(), repo, prismContext));
    }

    public ExclusionType toJAXB(PrismContext prismContext) throws DtoTranslationException {
        ExclusionType object = new ExclusionType();
        RExclusion.copyToJAXB(this, object, prismContext);
        return object;
    }
}
