package com.alibaba.csp.sentinel.slots.block.flow.param;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.block.flow.param.AbstractTimeBasedTest;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.util.TimeUtil;
import java.util.ArrayList;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

/**
 * @author quguai
 * @date 2023/10/27 13:44
 */
<<<<<<<HEAD
public class ParamFlowPartialIntegrationTest extends AbstractTimeBasedTest {
=======

  public class ParamFlowPartialIntegrationTest {
>>>>>>>upstream/master

    @Before
    public void setUp() throws Exception {
        ParamFlowRuleManager.loadRules(new ArrayList<>());
    }

    @After
    public void tearDown() throws Exception {
        ParamFlowRuleManager.loadRules(new ArrayList<>());
    }

    @Test
    public void testParamFlowRegex() {
<<<<<<<HEAD
        try (MockedStatic<TimeUtil> mocked = super.mockTimeUtil()) {
            setCurrentMillis(mocked, 1800000000000L);
            ParamFlowRule rule = new ParamFlowRule(".*")
                    .setParamIdx(0)
                    .setCount(1);
            rule.setRegex(true);
            ParamFlowRuleManager.loadRules(Collections.singletonList(rule));
            verifyFlow("testParamFlowRegex_1", true, "args");
            verifyFlow("testParamFlowRegex_1", true, "args_1");

            verifyFlow("testParamFlowRegex_1", false, "args");
            verifyFlow("testParamFlowRegex_1", false, "args_1");

            verifyFlow("testParamFlowRegex_2", true, "args");
            verifyFlow("testParamFlowRegex_2", true, "args_1");

            verifyFlow("testParamFlowRegex_2", false, "args");
            verifyFlow("testParamFlowRegex_2", false, "args_1");
        }
=======
      ParamFlowRule rule = new ParamFlowRule(".*")
          .setParamIdx(0)
          .setCount(1);
      rule.setRegex(true);
      ParamFlowRuleManager.loadRules(Collections.singletonList(rule));
      verifyFlow("testParamFlowRegex_1", true, "args");
      verifyFlow("testParamFlowRegex_1", true, "args_1");

      verifyFlow("testParamFlowRegex_1", false, "args");
      verifyFlow("testParamFlowRegex_1", false, "args_1");

      verifyFlow("testParamFlowRegex_2", true, "args");
      verifyFlow("testParamFlowRegex_2", true, "args_1");

      verifyFlow("testParamFlowRegex_2", false, "args");
      verifyFlow("testParamFlowRegex_2", false, "args_1");
>>>>>>>upstream / master
    }


    private void verifyFlow(String resource, boolean shouldPass, String... args) {
        Entry e = null;
        try {
            e = SphU.entry(resource, 1, EntryType.IN, args);
            assertTrue(shouldPass);
        } catch (BlockException e1) {
            assertFalse(shouldPass);
        } finally {
            if (e != null) {
                e.exit(1, args);
            }
        }
    }

}
