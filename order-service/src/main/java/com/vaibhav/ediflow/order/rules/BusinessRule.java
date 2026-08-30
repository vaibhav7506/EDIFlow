package com.vaibhav.ediflow.order.rules;
public interface BusinessRule { RuleResult evaluate(RuleContext context); int order(); }
