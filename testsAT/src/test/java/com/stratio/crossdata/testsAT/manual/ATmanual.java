package com.stratio.crossdata.testsAT.manual;

import org.testng.annotations.Test;

import com.stratio.cucumber.testng.CucumberRunner;
import com.stratio.tests.utils.BaseTest;

import cucumber.api.CucumberOptions;

@CucumberOptions(features = { "src/test/resources/features/manual" })
public class ATmanual extends BaseTest {

    public ATmanual() {
    }

    @Test(enabled = true, groups = {"manual"})
    public void manualTest() throws Exception {
        new CucumberRunner(this.getClass()).runCukes();
    }
}