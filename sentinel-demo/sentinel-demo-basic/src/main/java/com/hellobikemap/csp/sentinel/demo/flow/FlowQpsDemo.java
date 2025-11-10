/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hellobikemap.csp.sentinel.demo.flow;

import com.hellobikemap.csp.sentinel.Entry;
import com.hellobikemap.csp.sentinel.SphU;
import com.hellobikemap.csp.sentinel.context.ContextUtil;
import com.hellobikemap.csp.sentinel.slots.block.BlockException;
import com.hellobikemap.csp.sentinel.slots.block.RuleConstant;
import com.hellobikemap.csp.sentinel.slots.block.flow.FlowRule;
import com.hellobikemap.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.hellobikemap.csp.sentinel.util.TimeUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author jialiang.linjl
 */
public class FlowQpsDemo {

    private static final String KEY = "abc";

    private static AtomicInteger pass = new AtomicInteger();
    private static AtomicInteger block = new AtomicInteger();
    private static AtomicInteger total = new AtomicInteger();

    private static volatile boolean stop = false;

    private static final int threadCount = 32;

    private static int seconds = 60 + 40;

    public static void main(String[] args) throws Exception {
        initFlowQpsRule();

        tick();
      // tickMemory();
        // first make the system run on a very low condition
        simulateTraffic();

        System.out.println("===== begin to do flow control");
        System.out.println("only 20 requests per second can pass");

    }

    private static void initFlowQpsRule() {
        List<FlowRule> rules = new ArrayList<FlowRule>();
        FlowRule rule1 = new FlowRule();
        rule1.setResource(KEY);
      rule1.setClusterMode(false);

        // set limit qps to 20
      rule1.setCount(0.5);
        rule1.setGrade(RuleConstant.FLOW_GRADE_QPS);
      rule1.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_RATE_LIMITER);
        rule1.setLimitApp("default");
        rules.add(rule1);
        FlowRuleManager.loadRules(rules);
    }

    private static void simulateTraffic() {
        for (int i = 0; i < threadCount; i++) {
            Thread t = new Thread(new RunTask());
            t.setName("simulate-traffic-Task");
            t.start();
        }
    }

    private static void tick() {
        Thread timer = new Thread(new TimerTask());
        timer.setName("sentinel-timer-task");
        timer.start();
    }

  private static void tickMemory() {
    Thread timer = new Thread(new TimerMemoryTask());
    timer.setName("sentinel-timer-memory-task");
    timer.start();
  }

  static class TimerMemoryTask implements Runnable {

    @Override
    public void run() {
      long start = System.currentTimeMillis();
      System.out.println("begin to TimerMemoryTask!!!");

      Runtime runtime = Runtime.getRuntime();
      long initialMemory = runtime.totalMemory() - runtime.freeMemory();
      System.out.println("初始内存: " + initialMemory / 1024 / 1024 + "MB");

      while (!stop) {
        try {
          TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
        }
        // 记录当前内存
        long currentMemory = runtime.totalMemory() - runtime.freeMemory();
        System.out.println("当前内存: " + currentMemory / 1024 / 1024 + "MB");
        System.out.println(
            "内存增长: " + (currentMemory - initialMemory) / 1024 / 1024 + "MB");

      }

      long cost = System.currentTimeMillis() - start;
      System.out.println("time cost: " + cost + " ms");
      System.out.println("total:" + total.get() + ", pass:" + pass.get()
          + ", block:" + block.get());
      System.exit(0);
    }
  }
    static class TimerTask implements Runnable {

        @Override
        public void run() {
            long start = System.currentTimeMillis();
            System.out.println("begin to statistic!!!");

            long oldTotal = 0;
            long oldPass = 0;
            long oldBlock = 0;
            while (!stop) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                }
                long globalTotal = total.get();
                long oneSecondTotal = globalTotal - oldTotal;
                oldTotal = globalTotal;

                long globalPass = pass.get();
                long oneSecondPass = globalPass - oldPass;
                oldPass = globalPass;

                long globalBlock = block.get();
                long oneSecondBlock = globalBlock - oldBlock;
                oldBlock = globalBlock;

                System.out.println(seconds + " send qps is: " + oneSecondTotal);
                System.out.println(TimeUtil.currentTimeMillis() + ", total:" + oneSecondTotal
                    + ", pass:" + oneSecondPass
                    + ", block:" + oneSecondBlock);

                if (seconds-- <= 0) {
                    stop = true;
                }
            }

            long cost = System.currentTimeMillis() - start;
            System.out.println("time cost: " + cost + " ms");
            System.out.println("total:" + total.get() + ", pass:" + pass.get()
                + ", block:" + block.get());
            System.exit(0);
        }
    }

    static class RunTask implements Runnable {
        @Override
        public void run() {
            while (!stop) {
              ContextUtil.enter("web-request", "web-app");
                Entry entry = null;

                try {
                    entry = SphU.entry(KEY);
                    // token acquired, means pass
                    pass.addAndGet(1);
                } catch (BlockException e1) {
                    block.incrementAndGet();
                } catch (Exception e2) {
                    // biz exception
                } finally {
                    total.incrementAndGet();
                    if (entry != null) {
                        entry.exit();
                    }
                }

                Random random2 = new Random();
                try {
                    TimeUnit.MILLISECONDS.sleep(random2.nextInt(50));
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }
    }
}
