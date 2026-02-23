package top.yzljc.qqbot.feature.schedule;

import com.nlf.calendar.Lunar;
import com.nlf.calendar.Solar;
import top.yzljc.qqbot.botkits.userinfo.GetGroupInfo;
import top.yzljc.qqbot.botkits.image.AbstractImage;
import top.yzljc.qqbot.botkits.message.MessageSender;
import top.yzljc.qqbot.botkits.thread.ThreadManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.command.process.Command;
import top.yzljc.qqbot.command.process.CommandExecutor;
import top.yzljc.qqbot.command.process.CommandSender;
import top.yzljc.qqbot.config.ConfigFile;
import top.yzljc.qqbot.config.groups.GroupConfigManager;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.List;

public class Calendar implements CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(Calendar.class);

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.isDebug() && sender.isAdmin()) {
            sendToAllGroups();
        } else {
            sendToSingleGroup(sender.getGroupId());
        }
        return true;
    }

    private static class ImageGen extends AbstractImage {

        private static final Color TEXT_WHITE = new Color(255, 255, 255);
        private static final Color BG_CALENDAR = new Color(10, 10, 20, 130);
        private static final Color ACCENT_RED = new Color(255, 100, 100);
        private static final Color ACCENT_GOLD = new Color(255, 215, 0); // 节日配色
        private static final Color HIGHLIGHT_TODAY_BG = new Color(255, 255, 255, 210);
        private static final Color HIGHLIGHT_TODAY_TEXT = new Color(20, 20, 20);

        public void generate(File outFile) throws IOException {
            initFromBackground(ConfigFile.IMG_CALENDER.getFileName());
            Graphics2D g2d = g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            LocalDate now = LocalDate.now();
            // LocalDate now = LocalDate.of(2027, 1, 3); // 别删这行老子debug用

            Solar solar = new Solar(now.getYear(), now.getMonthValue(), now.getDayOfMonth());
            Lunar lunar = solar.getLunar();

            String lunarDateStr = lunar.getMonthInChinese() + "月" + lunar.getDayInChinese();
            String jieqi = lunar.getJieQi();
            String lunarFestival = "";
            if (!lunar.getFestivals().isEmpty()) {
                lunarFestival = lunar.getFestivals().getFirst();
            }

            int leftMargin = 50;
            int cursorY = 100;

            drawOutlinedText(g2d, String.format("%d / %02d", now.getYear(), now.getMonthValue()),
                    loadFont(Font.BOLD, 28f), leftMargin, cursorY, TEXT_WHITE);

            cursorY += 85;
            drawOutlinedText(g2d, String.format("%02d", now.getDayOfMonth()),
                    loadFont(Font.BOLD, 80f), leftMargin, cursorY, TEXT_WHITE);

            int dateWidth = g2d.getFontMetrics(loadFont(Font.BOLD, 100f)).stringWidth(String.format("%02d", now.getDayOfMonth()));
            String weekStr = "星期" + getChineseWeekName(now.getDayOfWeek());
            drawOutlinedText(g2d, weekStr, loadFont(Font.PLAIN, 32f), leftMargin + dateWidth + 25, cursorY, TEXT_WHITE);

            cursorY += 50;
            drawOutlinedText(g2d, "农历 " + lunarDateStr + "  " + lunar.getYearInGanZhi() + "年",
                    loadFont(Font.PLAIN, 28f), leftMargin, cursorY, TEXT_WHITE);

            if (!jieqi.isEmpty() || !lunarFestival.isEmpty()) {
                cursorY += 45;
                String specialText = (jieqi.isEmpty() ? "" : "◆ " + jieqi + "  ") + (lunarFestival.isEmpty() ? "" : "★ " + lunarFestival);
                drawOutlinedText(g2d, specialText, loadFont(Font.BOLD, 28f), leftMargin, cursorY, ACCENT_RED);
            }

            Holiday nextHoliday = getNextHoliday(now);
            long daysUntil = ChronoUnit.DAYS.between(now, nextHoliday.date);

            int bottomY = height - 60;

            drawOutlinedText(g2d, "距离 " + nextHoliday.name + " 还有", loadFont(Font.PLAIN, 24f), leftMargin, bottomY - 60, TEXT_WHITE);
            drawOutlinedText(g2d, String.valueOf(daysUntil), loadFont(Font.BOLD, 55f), leftMargin, bottomY, ACCENT_RED);

            int daysNumW = g2d.getFontMetrics(loadFont(Font.BOLD, 65f)).stringWidth(String.valueOf(daysUntil));
            drawOutlinedText(g2d, " 天", loadFont(Font.BOLD, 24f), leftMargin + daysNumW + 10, bottomY, TEXT_WHITE);


            int calW = 420;
            int calH = 350;
            int calX = width - calW - 30; // 靠右
            int calY = 50; // 靠上

            // 背景
            g2d.setColor(BG_CALENDAR);
            g2d.fill(new RoundRectangle2D.Float(calX, calY, calW, calH, 15, 15));

            // 月份标题
            String monthEn = now.getMonth().name();
            monthEn = monthEn.charAt(0) + monthEn.substring(1).toLowerCase();
            drawCenteredText(g2d, monthEn + " " + now.getYear(), loadFont(Font.BOLD, 22f), calX, calY + 30, calW, TEXT_WHITE);

            // 表头
            String[] headers = {"一", "二", "三", "四", "五", "六", "日"};
            int cellW = calW / 7;
            int startHeaderY = calY + 60;

            g2d.setFont(loadFont(Font.BOLD, 14f));
            for (int i = 0; i < 7; i++) {
                int cellX = calX + (i * cellW);
                drawCenteredText(g2d, headers[i], g2d.getFont(), cellX, startHeaderY, cellW, new Color(255, 255, 255, 180));
            }

            // 日期网格
            drawCalendarGrid(g2d, now, calX, startHeaderY + 15, cellW, 38);

            String watermark = "ATRI - 哔哩哔哩 BV1GrZTBDEJt";
            g2d.setFont(loadFont(Font.PLAIN, 20f));
            FontMetrics fm = g2d.getFontMetrics();
            int wmWidth = fm.stringWidth(watermark);
            drawOutlinedText(g2d, watermark, g2d.getFont(), width - wmWidth - 15, height - 15, new Color(9, 215, 239, 150));

            g2d.setFont(loadFont(Font.PLAIN, 16f)); // 设置12号字体
            Color wmColor = new Color(255, 255, 255, 150); // 半透明白色

            drawOutlinedText(g2d, "图片PixivID: 138888613", g2d.getFont(), 15, height - 15, wmColor);

            saveAndDispose(outFile);
        }

        private void drawOutlinedText(Graphics2D g, String text, Font font, int x, int y, Color color) {
            if (text == null || text.isEmpty()) return;
            g.setFont(font);
            FontRenderContext frc = g.getFontRenderContext();
            TextLayout tl = new TextLayout(text, font, frc);
            Shape shape = tl.getOutline(null);
            g.translate(x, y);
            g.setColor(new Color(0, 0, 0, 180));
            g.setStroke(new BasicStroke(3.0f));
            g.draw(shape);
            g.setColor(color);
            g.fill(shape);
            g.translate(-x, -y);
        }

        private void drawCalendarGrid(Graphics2D g, LocalDate today, int startX, int startY, int cellW, int cellH) {
            LocalDate firstDayOfMonth = today.with(TemporalAdjusters.firstDayOfMonth());
            int daysInMonth = today.lengthOfMonth();
            int startDayOfWeek = firstDayOfMonth.getDayOfWeek().getValue() - 1;

            int currentDay = 1;
            int row = 0;

            while (currentDay <= daysInMonth) {
                for (int col = 0; col < 7; col++) {
                    if (row == 0 && col < startDayOfWeek) continue;
                    if (currentDay > daysInMonth) break;

                    int x = startX + (col * cellW);
                    int y = startY + (row * cellH);

                    boolean isToday = (currentDay == today.getDayOfMonth());

                    // 今天的高亮背景
                    if (isToday) {
                        g.setColor(HIGHLIGHT_TODAY_BG);
                        int size = Math.min(cellW, cellH) - 4;
                        g.fillOval(x + (cellW - size) / 2, y + (cellH - size) / 2 - 12, size, size);
                        g.setColor(HIGHLIGHT_TODAY_TEXT);
                    } else {
                        g.setColor(TEXT_WHITE);
                    }

                    g.setFont(loadFont(Font.BOLD, 16f));
                    drawCenteredText(g, String.valueOf(currentDay), g.getFont(), x, y + 12, cellW, g.getColor());

                    Solar daySolar = new Solar(today.getYear(), today.getMonthValue(), currentDay);
                    Lunar dayLunar = daySolar.getLunar();

                    String bottomText = dayLunar.getDayInChinese(); // 默认：初一、十五
                    Color bottomColor = isToday ? HIGHLIGHT_TODAY_TEXT : new Color(200, 200, 200);

                    List<String> festivals = dayLunar.getFestivals();
                    if (!festivals.isEmpty()) {
                        bottomText = festivals.getFirst();
                        if (bottomText.length() > 3) bottomText = bottomText.substring(0, 3); // 截断过长节日
                        bottomColor = isToday ? HIGHLIGHT_TODAY_TEXT : ACCENT_GOLD; // 节日金色
                    } else {
                        String jq = dayLunar.getJieQi();
                        if (jq != null && !jq.isEmpty()) {
                            bottomText = jq;
                            bottomColor = isToday ? HIGHLIGHT_TODAY_TEXT : ACCENT_RED; // 节气红色
                        } else if (dayLunar.getDay() == 1) {
                            bottomText = dayLunar.getMonthInChinese() + "月"; // 初一显示月份
                            bottomColor = isToday ? HIGHLIGHT_TODAY_TEXT : ACCENT_RED;
                        }
                    }

                    float fontSize = bottomText.length() > 2 ? 9f : 10f;
                    g.setFont(loadFont(Font.PLAIN, fontSize));
                    drawCenteredText(g, bottomText, g.getFont(), x, y + 24, cellW, bottomColor);

                    currentDay++;
                }
                row++;
            }
        }

        private void drawCenteredText(Graphics2D g, String text, Font font, int x, int y, int w, Color c) {
            g.setColor(c);
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics();
            int textW = fm.stringWidth(text);
            g.drawString(text, x + (w - textW) / 2, y);
        }

        private String getChineseWeekName(DayOfWeek dayOfWeek) {
            String[] weeks = {"一", "二", "三", "四", "五", "六", "日"};
            return weeks[dayOfWeek.getValue() - 1];
        }

        private Holiday getNextHoliday(LocalDate now) {
            int year = now.getYear();
            List<Holiday> holidays = new ArrayList<>();

            holidays.add(new Holiday("元旦", LocalDate.of(year, 1, 1)));
            holidays.add(new Holiday("妇女节", LocalDate.of(year, 3, 8)));
            holidays.add(new Holiday("植树节", LocalDate.of(year, 3, 12)));
            holidays.add(new Holiday("劳动节", LocalDate.of(year, 5, 1)));
            holidays.add(new Holiday("青年节", LocalDate.of(year, 5, 4)));
            holidays.add(new Holiday("儿童节", LocalDate.of(year, 6, 1)));
            holidays.add(new Holiday("建党节", LocalDate.of(year, 7, 1)));
            holidays.add(new Holiday("建军节", LocalDate.of(year, 8, 1)));
            holidays.add(new Holiday("教师节", LocalDate.of(year, 9, 10)));
            holidays.add(new Holiday("国庆节", LocalDate.of(year, 10, 1)));
            holidays.add(new Holiday("圣诞节", LocalDate.of(year, 12, 25)));
            holidays.add(new Holiday("元旦", LocalDate.of(year + 1, 1, 1)));

            addLunarHolidays(year, holidays);
            addLunarHolidays(year + 1, holidays);

            holidays.sort(Comparator.comparing(h -> h.date));

            for (Holiday h : holidays) {
                if (h.date.isAfter(now)) {
                    return h;
                }
            }
            return holidays.getFirst();
        }

        private void addLunarHolidays(int year, List<Holiday> list) {
            int[][] lunarFestivals = {
                    {1, 1, 0},   // 春节 (Name index 0)
                    {1, 15, 1},  // 元宵节
                    {5, 5, 2},   // 端午节
                    {7, 7, 3},   // 七夕节
                    {8, 15, 4},  // 中秋节
                    {9, 9, 5},   // 重阳节
                    {12, 8, 6},  // 腊八节
                    {12, 23, 7}, // 小年
            };
            String[] names = {"春节", "元宵节", "端午节", "七夕", "中秋节", "重阳节", "腊八", "小年"};

            for (int[] item : lunarFestivals) {
                Lunar l = Lunar.fromYmd(year, item[0], item[1]);
                Solar s = l.getSolar();
                list.add(new Holiday(names[item[2]], LocalDate.of(s.getYear(), s.getMonth(), s.getDay())));
            }

            Lunar nextYearFirst = Lunar.fromYmd(year + 1, 1, 1);
            Solar s = nextYearFirst.getSolar();
            LocalDate cny = LocalDate.of(s.getYear(), s.getMonth(), s.getDay());
            list.add(new Holiday("除夕", cny.minusDays(1)));
        }

        static class Holiday {
            String name;
            LocalDate date;

            public Holiday(String name, LocalDate date) {
                this.name = name;
                this.date = date;
            }
        }
    }

    public static void generateDevelopDayImage() throws IOException {
        File tempDir = new File("tmp");
        if (!tempDir.exists()) tempDir.mkdirs();
        File outFile = new File(tempDir, "daily_schedule.png");
        new ImageGen().generate(outFile);
    }

    public static void sendToSingleGroup(long targetGroupId) {
        File tempFile = new File("tmp", "daily_schedule.png");
        try {
            generateDevelopDayImage();
            if (tempFile.exists()) {
                byte[] imgBytes = Files.readAllBytes(tempFile.toPath());
                String base64Img = Base64.getEncoder().encodeToString(imgBytes);
                MessageSender.sendGroupMessage(targetGroupId, null, base64Img);
            }
        } catch (Exception e) {
            log.error("发送失败", e);
        } finally {
            if (tempFile.exists()) tempFile.delete();
        }
    }

    public static void sendToAllGroups() {
        File tempFile = new File("tmp", "daily_schedule.png");
        ThreadManager.execute(() -> {
            try {
                generateDevelopDayImage();
                byte[] imgBytes = Files.readAllBytes(tempFile.toPath());
                String base64Img = Base64.getEncoder().encodeToString(imgBytes);
                for (long gid : GetGroupInfo.fetchAllGroupIds()) {
                    if (!GroupConfigManager.isFeatureEnabled(gid, "calendar")) continue;
                    MessageSender.sendGroupMessage(gid, null, base64Img);
                }
                log.info("日历推送完成");
            } catch (Exception ex) {
                log.error("日历推送异常：", ex);
            } finally {
                if (tempFile.exists()) tempFile.delete();
            }
        });
    }
}