/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *  $Id$
 */

package org.exist.validation.test;
import junit.framework.Assert;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.exist.validation.service.ValidationService;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;


/**
 *  jUnit test for testing the Validation Service.
 *
 * @author dizzzz
 */
public class ValidationServiceTest  extends TestCase {
    
    private final static String URI = "xmldb:exist:///db";
    private final static String DRIVER = "org.exist.xmldb.DatabaseImpl";
    private Collection rootCollection = null;
    private ValidationService service = null;    
    private String eXistHome = null;
    
    public ValidationServiceTest(String testName) {
        super(testName);
    }
    
    public static Test suite() {
        TestSuite suite = new TestSuite(ValidationServiceTest.class);
        return suite;
    }
    
    public void setUp() {
    	try {
	        System.out.println(">>> setUp");
	        eXistHome = System.getProperty("exist.home");
	        
	        Class cl = Class.forName(DRIVER);
	        Database database = (Database) cl.newInstance();
	        database.setProperty("create-database", "true");
	        DatabaseManager.registerDatabase(database);
	        rootCollection = DatabaseManager.getCollection(URI, "admin", null);
	        assertNotNull("Could not connect to database.");  
	        service = getValidationService();        
	        System.out.println("<<<\n");
        } catch (Exception e) {            
            fail(e.getMessage());  
        }
    }
    
    private ValidationService getValidationService() {
    	try {
    		return (ValidationService) rootCollection.getService("ValidationService", "1.0");
        } catch (Exception e) {            
            fail(e.getMessage());  
        }    
        return null;
    }
    
    public void testGetName() {
    	try {    
        Assert.assertEquals("ValidationService check", service.getName(),  "ValidationService" );
	    } catch (Exception e) {            
	        fail(e.getMessage());  
	    }    
    }
    
    public void testGetVersion() {
    	try {    
        assertEquals("ValidationService check", service.getVersion(),   "1.0" );
	    } catch (Exception e) {            
	        fail(e.getMessage());  
	    }    
	 }
    
    public void testXsdValidDocument() {   
    	try {   
    		assertTrue( service.validateResource("/db/addressbook_valid.xml") );
	    } catch (Exception e) {            
	        fail(e.getMessage());  
	    }    
    }
    
    public void testXsdInvalidDocument() {
    	try {       
    		assertFalse( service.validateResource("/db/addressbook_invalid.xml") );
	    } catch (Exception e) {            
	        fail(e.getMessage());  
	    }    
    }
    
    public void testNonexistingDocument() {
    	try {
    		assertFalse( service.validateResource("/db/foobar.xml") );
        } catch (Exception e) {            
            fail(e.getMessage());  
        }          
    }
    
    public void testDtdValidDocument() {
    	try {       
    		assertTrue( service.validateResource("/db/hamlet_valid.xml") );
        } catch (Exception e) {            
            fail(e.getMessage());  
        }    
    }
    
    public void testDtdInvalidDocument() {
    	try {
            assertFalse( service.validateResource("/db/hamlet_invalid.xml") );
        } catch (Exception e) {            
            fail(e.getMessage());  
        }    
    }
}
