/*
 * Copyright (c) xlightweb.org, 2006 - 2010. All rights reserved.
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * Please refer to the LGPL license at: http://www.gnu.org/copyleft/lesser.txt
 * The latest copy of this software may be found on http://www.xsocket.org/
 */
package org.xsocket;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.DynamicMBean;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.ReflectionException;


/**
 * Introspection based dynamic mbean, which exposes the getter and setter methods 
 * (default, protected and public visibility) of the underlying object by using introspection <br>
 * 
 * <br/><br/><b>This is a xSocket internal class and subject to change</b>  
 * 
 * @author grro@xsocket.org
 */
public final class IntrospectionBasedDynamicMBean implements DynamicMBean {
	
	
	private static final Logger LOG = Logger.getLogger(IntrospectionBasedDynamicMBean.class.getName());
	
	private Object obj;
	
	private final Map<String, Info> properties = new HashMap<String, Info>();

	
	/**
	 * constructor
	 *  
	 * @param obj  the object to create a mbean for
	 */
	public IntrospectionBasedDynamicMBean(Object obj) {
		this.obj = obj;
	}
	
	
	/**
	 * @see javax.management.DynamicMBean#getAttribute(java.lang.String)
	 */
	public Object getAttribute(String attribute) {
		String methodName = "get" + attribute;
		try {
			Method method = getMethod(obj.getClass(), methodName, new Class[0]);
			
			if (method == null) {
				methodName = "is" + attribute;
				method = getMethod(obj.getClass(), methodName, new Class[0]);
			}
			
			method.setAccessible(true);
			return method.invoke(obj, new Object[0]);


		} catch (InvocationTargetException ite) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured (InvocationTargetException) by accessing attribute " + attribute + ": " + ite.toString());
			}
			return null;
			
		} catch (IllegalAccessException iae) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured (IllegalAccessException) by accessing attribute " + attribute + ": " + iae.toString());
			}
			return null;
		}
	}
	
	
	@SuppressWarnings("unchecked")
	private Method getMethod(Class clazz, String methodname, Class[] params) {
		do {
			try {
				Method method = clazz.getDeclaredMethod(methodname, params);
				method.setAccessible(true);
				return method;
			} catch (NoSuchMethodException ignore) { }
			
			for (Class interf : clazz.getInterfaces()) {
				getMethod(interf, methodname, params);
			}
			
			clazz = clazz.getSuperclass();
			
		} while (clazz != null);
		
		return null;
	}
	

	/**
	 * {@inheritDoc}
	 */
	public AttributeList getAttributes(String[] attributes)  {
		AttributeList list = new AttributeList();
		for (String attribute : attributes) {
			list.add(new Attribute(attribute, getAttribute(attribute)));
		}
		return list;
	}


	/**
	 * {@inheritDoc}
	 */
	public void setAttribute(Attribute attribute) {
		String methodName = "set" + attribute.getName();
		
		Info info = getInfo(attribute.getName()); 
		
		try {
			Method method = getMethod(obj.getClass(), methodName, new Class[] { info.propertyType }); 
			method.setAccessible(true);
			method.invoke(obj, new Object[] { attribute.getValue() });
		} catch (InvocationTargetException ite) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured (InvocationTargetException) by setting attribute " + ite.toString());
			}

		} catch (IllegalAccessException iae) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured (IllegalAccessException) by setting attribute " + iae.toString());
			}
		}
	}


	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public AttributeList setAttributes(AttributeList attributes) {
		AttributeList result = new AttributeList();

		Attribute[] attrs = (Attribute[]) attributes.toArray(new Attribute[attributes.size()]);
        for (Attribute attr : attrs) {
        	setAttribute(attr);
        	result.add(new Attribute(attr.getName(), attr.getValue()));	
        }
        return result;
	}
	
	

	/**
	 * {@inheritDoc}
	 */
	public Object invoke(String actionName, Object[] params, String[] signature) throws MBeanException, ReflectionException {
		return null;
	}
	

	/**
	 * {@inheritDoc}
	 */
	public synchronized MBeanInfo getMBeanInfo() {
		
		analyze(obj);

		String[] attributes = properties.keySet().toArray(new String[properties.size()]);
		MBeanAttributeInfo[] attrs = new MBeanAttributeInfo[attributes.length];
		for (int i = 0; i < attrs.length; i++) {
			attrs[i] = properties.get(attributes[i]).asbMBeanAttributeInfo();
		}

	        
		return new MBeanInfo(
				obj.getClass().getName(),
				"",
				attrs,
				null,  // constructors
				null,
				null); // notifications
	    }
	
	
	@SuppressWarnings("unchecked")
	private void analyze(Object obj) {
		Class clazz = obj.getClass();
		do {
			analyzeType(clazz);
			
			for (Class interf: clazz.getInterfaces()) {
				analyzeType(interf);
			}
			
			clazz = clazz.getSuperclass();
			
		} while (clazz != null);
	}
	
	@SuppressWarnings("unchecked")
	private void analyzeType(Class clazz) {
		for (Method method : clazz.getDeclaredMethods()) {
			String name = method.getName();
			if (Modifier.isPrivate(method.getModifiers())) {
				continue;
			}
			
			if ((name.length() > 3) && name.startsWith("get") && (method.getParameterTypes().length == 0)) {
				Class propertyType = method.getReturnType();
				
				if(isAcceptedPropertyType(propertyType)) {
					Info info = getInfo(name.substring(3, name.length()));
					info.isReadable = true;
					info.propertyType = propertyType;
				}
			} 
			
			
			if ((name.length() > 2) && name.startsWith("is") && (method.getParameterTypes().length == 0)) {
				Class propertyType = method.getReturnType();
				
				if(isAcceptedPropertyType(propertyType)) {
					Info info = getInfo(name.substring(2, name.length()));
					info.isReadable = true;
					info.propertyType = propertyType;
				}
			} 
			
			if ((name.length() > 3) && name.startsWith("set") && (method.getParameterTypes().length == 1)) {
				Class propertyType = method.getParameterTypes()[0];
					
				if(isAcceptedPropertyType(propertyType)) {
					Info info = getInfo(name.substring(3, name.length()));
					info.isWriteable = true;
					info.propertyType = propertyType;
				}
			}
		}
	}
	
	
	private Info getInfo(String name) {
		Info info = properties.get(name);
		if (info == null) {
			info = new Info();
			info.propertyName = name;
			info.propertyDescription = "Property " + info.propertyName;
			properties.put(name, info);
		}
		return info;
	}
	
	
	
	
	@SuppressWarnings("unchecked")
	private boolean isAcceptedPropertyType(Class clazz) {
		if (clazz.isAssignableFrom(List.class)) {
			return true;
		}
		
		String name = clazz.getName();
		return name.equals("int")
		       || name.equals("java.lang.Integer")
			   || name.equals("long")
			   || name.equals("java.lang.Long")
			   || name.equals("double")
			   || name.equals("java.lang.Double")
			   || name.equals("boolean")
			   || name.equals("java.lang.Boolean")
			   || name.equals("float")
               || name.equals("java.lang.String")
               || name.equals("java.util.Date")
			   || name.equals("java.lang.Float");
	}

	
	private static class Info {
		String propertyName = null;
		@SuppressWarnings("unchecked")
		Class  propertyType = null;
		String propertyDescription = null;
		boolean isReadable = false;
		boolean isWriteable = false;
		boolean isIs = false;


		MBeanAttributeInfo asbMBeanAttributeInfo() {
			return new MBeanAttributeInfo(propertyName, propertyType.getName(), propertyDescription, isReadable, isWriteable, isIs);
		}
	}
}