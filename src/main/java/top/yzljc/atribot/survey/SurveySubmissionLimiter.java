package top.yzljc.atribot.survey;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.base.Ticker;
import top.yzljc.atribot.webui.ApiCode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * 重复填写问卷的内存频控，同一账号对同一份问卷在滚动一小时内最多提交五份
 */
public final class SurveySubmissionLimiter {
    private static final Duration WINDOW = Duration.ofHours(1);
    private final Ticker ticker;
    private final Cache<Key, List<Entry>> entries;

    public SurveySubmissionLimiter() { this(System::nanoTime); }

    SurveySubmissionLimiter(LongSupplier nanoTime) {
        this.ticker = new Ticker() {
            @Override
            public long read() { return nanoTime.getAsLong(); }
        };
        entries = CacheBuilder.newBuilder().ticker(ticker).expireAfterAccess(WINDOW).build();
    }

    private record Key(String surveyId, String accountId) {}

    private static final class Entry {
        private boolean pending = true;
        private long completedAt;
    }

    /**
     * 创建提交凭证，校验通过后预占名额，事务提交成功后确认，失败时自动释放
     *
     * @param surveyId 问卷短编号
     * @param accountId 统一账号 UUID
     * @return 需要关闭的提交凭证
     */
    public Attempt attempt(String surveyId, String accountId) {
        return new Attempt(new Key(surveyId, accountId));
    }

    public final class Attempt implements AutoCloseable {
        private final Key key;
        private Entry entry;
        private boolean committed;

        private Attempt(Key key) { this.key = key; }

        /** 预占一个名额，超限时返回问卷频控业务错误 */
        public void reserve() {
            if (entry != null) throw new IllegalStateException("提交凭证已占用名额");
            entries.asMap().compute(key, (ignored, history) -> {
                if (history == null) history = new ArrayList<>(5);
                long now = ticker.read();
                history.removeIf(item -> !item.pending && now - item.completedAt >= WINDOW.toNanos());
                if (history.size() >= 5) throw new SurveyDefinition.Failure(ApiCode.SURVEY_RATE_LIMITED);
                entry = new Entry();
                history.add(entry);
                return history;
            });
        }

        /** 数据库事务提交成功后开始计算名额的有效期 */
        public void commit() {
            if (entry == null) return;
            entries.asMap().computeIfPresent(key, (ignored, history) -> {
                entry.completedAt = ticker.read();
                entry.pending = false;
                return history;
            });
            committed = true;
        }

        /** 释放未完成提交占用的名额，成功提交的记录保留至过期 */
        @Override
        public void close() {
            if (entry == null || committed) return;
            entries.asMap().computeIfPresent(key, (ignored, history) -> {
                history.remove(entry);
                return history.isEmpty() ? null : history;
            });
        }
    }
}
