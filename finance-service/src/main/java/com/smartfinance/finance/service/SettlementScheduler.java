package com.smartfinance.finance.service;

import com.smartfinance.finance.entity.Transaction;
import com.smartfinance.finance.entity.TransactionType;
import com.smartfinance.finance.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementScheduler {

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;

    /** Corre no arranque do serviço para processar transações em atraso. */
    @EventListener(ApplicationReadyEvent.class)
    public void settleOnStartup() {
        settleTransactions();
    }

    /**
     * Corre todos os dias à meia-noite.
     * Encontra transações agendadas cuja data chegou e aplica-as ao saldo da conta.
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void settleTransactions() {
        LocalDate today = LocalDate.now();
        List<Transaction> due = transactionRepository.findUnsettledDue(today);

        if (due.isEmpty()) return;

        log.info("Settling {} scheduled transaction(s) for {}", due.size(), today);

        for (Transaction t : due) {
            BigDecimal delta = t.getType() == TransactionType.INCOME
                    ? t.getAmount()
                    : t.getAmount().negate();
            accountService.adjustBalance(t.getAccount().getId(), delta);
            t.setSettled(true);
        }

        transactionRepository.saveAll(due);
        log.info("Settlement complete.");
    }
}
