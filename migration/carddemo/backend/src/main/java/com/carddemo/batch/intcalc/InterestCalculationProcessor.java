package com.carddemo.batch.intcalc;

import org.springframework.batch.item.ItemProcessor;

/**
 * One account's worth of CBACT04C's main loop (CBACT04C.cbl:194-217).
 */
class InterestCalculationProcessor implements ItemProcessor<AccountBalanceGroup, InterestPosting> {

    private final InterestCalculator calculator;

    InterestCalculationProcessor(InterestCalculator calculator) {
        this.calculator = calculator;
    }

    @Override
    public InterestPosting process(AccountBalanceGroup group) {
        return calculator.post(group);
    }
}
