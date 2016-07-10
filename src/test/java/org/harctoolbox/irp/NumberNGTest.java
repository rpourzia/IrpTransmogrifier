/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.harctoolbox.irp;

import org.antlr.v4.runtime.misc.ParseCancellationException;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 *
 * @author bengt
 */
public class NumberNGTest {

    public NumberNGTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @BeforeMethod
    public void setUpMethod() throws Exception {
    }

    @AfterMethod
    public void tearDownMethod() throws Exception {
    }

    /**
     * Test of parse method, of class Number.
     */
    @Test
    public void testParse_String() {
        System.out.println("parse");
        try {
            assertEquals(Number.parse("UINT8_MAX"), 255L);
            assertEquals(Number.parse("UINT16_MAX"), 65535L);
            assertEquals(Number.parse("UINT24_MAX"), 16777215L);
            assertEquals(Number.parse("UINT32_MAX"), 4294967295L);
            assertEquals(Number.parse("UINT64_MAX"), -1L);
            assertEquals(Number.parse("073"), 59);
            assertEquals(Number.parse("0"), 0L);
            assertEquals(Number.parse("123456789"), 123456789);
            assertEquals(Number.parse("0xdeadbeef"), 0xdeadbeefL);
            assertEquals(Number.parse("0xdeadBeef"), 0xdeadbeefL);
        } catch (ParseCancellationException ex) {
            fail();
        }

    }
}