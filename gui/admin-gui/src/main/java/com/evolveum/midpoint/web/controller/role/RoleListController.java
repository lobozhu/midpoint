/*
 * Copyright (c) 2011 Evolveum
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
 * Portions Copyrighted 2011 [name of copyright owner]
 */
package com.evolveum.midpoint.web.controller.role;

import java.io.Serializable;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

/**
 * 
 * @author lazyman
 * 
 */
@Controller("roleList")
@Scope("session")
public class RoleListController implements Serializable {

	private static final long serialVersionUID = -2220151123394281052L;
	public static final String PAGE_NAVIGATION_LIST = "/role/index?faces-redirect=true";

	public String initController() {

		return PAGE_NAVIGATION_LIST;
	}
}
