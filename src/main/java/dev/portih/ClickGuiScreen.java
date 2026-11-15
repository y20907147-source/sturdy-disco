package dev.portih.gui;

import dev.portih.Config;
import dev.portih.Draw;
import dev.portih.PortihClient;
import dev.portih.basefinder.BaseFinderConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ClickGUI: окно с боковой панелью категорий, поиском сверху и сеткой карточек модулей (2 колонки).
 *
 * Структура: ClickGuiScreen (окно, ввод, раскладка) -> Module (карточка) -> GuiComponent
 * (BoolSetting / NumberSetting / ModeSetting / ColorSetting / ButtonSetting).
 * RenderUtil - рисование и палитра, AnimationUtil - анимации по dt.
 *
 * Мышь читается напрямую из GLFW (как в ConfigScreen), поэтому код не зависит от версии событий ввода.
 * Клавиатура: keyPressed / charTyped (нужны для поиска и точного ввода чисел).
 */
public class ClickGuiScreen extends Screen {
    private static final int BASE_W = 620, BASE_H = 364;
    private static final int SB_W = 124, TOP_H = 46, GAP = 8, SEARCH_W = 190;

    private final Screen parent;
    private final long openedAt = System.currentTimeMillis();
    private final AnimationUtil.FrameClock clock = new AnimationUtil.FrameClock();
    private final String closeKeyName;

    private float dt = 0.016f;
    private float openAnim;
    private boolean closing;

    // окно
    private float winX, winY;
    private int winW, winH;
    private boolean placed;
    private boolean draggingWin;
    private float grabX, grabY;

    // состояние
    private Category category;
    private String query = "";
    private String qLower = "";
    private String queryView = "";
    private boolean searchFocus;
    private float searchAnim, closeHover, catFade = 1f, tabSel = -1f;
    private final float[] catHover = new float[Category.values().length];

    // прокрутка
    private float scrollTarget, scrollCur, contentH;

    // ввод
    private boolean wasLeft, wasRight;
    private GuiComponent active;   // слайдер/цвет, который сейчас тянут
    private GuiComponent typing;   // поле точного ввода

    // раскладка
    private final List<Module> shown = new ArrayList<>();
    private int vx, vy, vw, vh;
    private int searchX, searchY;

    public ClickGuiScreen(Screen parent) {
        super(Component.literal("PortihMod"));
        this.parent = parent;
        this.category = Category.byIndex(Config.get().guiCategory);
        String k = PortihClient.openMenu != null ? PortihClient.openMenu.getTranslatedKeyMessage().getString() : "Insert";
        this.closeKeyName = k;
        ModuleManager.all();
    }

    @Override
    protected void init() {
        placeWindow();
    }

    // ------------------------------------------------------------------ окно

    private void placeWindow() {
        winW = Math.min(BASE_W, this.width - 16);
        winH = Math.min(BASE_H, this.height - 16);
        if (!placed) {
            Config c = Config.get();
            winX = c.guiX >= 0 ? c.guiX : (this.width - winW) / 2f;
            winY = c.guiY >= 0 ? c.guiY : (this.height - winH) / 2f;
            placed = true;
        }
        clampWindow();
    }

    private void clampWindow() {
        winX = Math.max(0f, Math.min(this.width - winW, winX));
        winY = Math.max(0f, Math.min(this.height - winH, winY));
    }

    @Override
    public void onClose() {
        closing = true;   // реально закроемся, когда доиграет анимация (см. render)
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // свой фон рисуется в render(); ванильное размытие отключено
    }

    @Override
    public void removed() {
        Config c = Config.get();
        c.guiX = Math.round(winX);
        c.guiY = Math.round(winY);
        c.guiCategory = category.ordinal();
        Config.save();
        BaseFinderConfig.save();
    }

    // ------------------------------------------------------------------ кадр

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        dt = clock.tick();
        openAnim = AnimationUtil.approach(openAnim, closing ? 0f : 1f, dt, 16f);
        if (closing && openAnim <= 0.01f) {
            super.onClose();     // setScreen(null)
            return;
        }
        float e = AnimationUtil.easeOut(openAnim);
        float scale = 0.92f + 0.08f * e;
        clampWindow();
        float cx = winX + winW / 2f, cy = winY + winH / 2f;
        // мышь переводим в координаты "до масштабирования", чтобы попадание было точным во время анимации
        int lmx = Math.round((mouseX - cx) / scale + cx);
        int lmy = Math.round((mouseY - cy) / scale + cy);

        RenderUtil.begin(g, this.font, e, Math.max(0.5f, Math.min(1f, Config.get().guiOpacity)));
        g.fill(0, 0, this.width, this.height, Draw.fade(0xFF000000, 0.42f * e));

        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(scale, scale);
        g.pose().translate(-cx, -cy);

        catFade = AnimationUtil.approach(catFade, 1f, dt, 12f);
        updateShown();
        for (Module m : shown) m.update(dt);
        updateScroll();
        layout();
        if (typing != null && !typing.isEditing()) typing = null;

        if (openAnim > 0.85f && !closing) pollMouse(lmx, lmy);
        else {
            wasLeft = false;
            wasRight = false;
        }

        drawWindow(lmx, lmy);
        g.pose().popMatrix();
    }

    private void updateShown() {
        shown.clear();
        for (Module m : ModuleManager.all()) {
            if (qLower.isEmpty() ? m.category == category : m.matches(qLower)) shown.add(m);
        }
    }

    private void updateScroll() {
        float max = Math.max(0f, contentH - vh);
        scrollTarget = Math.max(0f, Math.min(max, scrollTarget));
        scrollCur = AnimationUtil.approach(scrollCur, scrollTarget, dt, 14f);
    }

    private void layout() {
        int wx = Math.round(winX), wy = Math.round(winY);
        vx = wx + SB_W + 8;
        vy = wy + TOP_H;
        vw = winW - SB_W - 8 - 14;
        vh = winH - TOP_H - 12;
        searchX = wx + winW - 14 - 24 - 10 - SEARCH_W;
        searchY = wy + 11;

        int cardsW = vw - 8;
        int colW = (cardsW - GAP) / 2;
        float[] colY = {0f, 0f};
        float slide = (1f - catFade) * 10f;
        for (int i = 0; i < shown.size(); i++) {
            int col = i & 1;
            Module m = shown.get(i);
            m.layout(vx + col * (colW + GAP), vy - scrollCur + colY[col] + slide, colW);
            colY[col] += m.height() + GAP;
        }
        contentH = Math.max(0f, Math.max(colY[0], colY[1]) - GAP);
    }

    // ------------------------------------------------------------------ ввод мышью

    private void pollMouse(int mx, int my) {
        long handle = GLFW.glfwGetCurrentContext();
        boolean left = handle != 0L
                && GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean right = handle != 0L
                && GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

        if (System.currentTimeMillis() - openedAt > 150L) {
            if (left && !wasLeft) press(mx, my, 0);
            if (right && !wasRight) press(mx, my, 1);
            if (!left && wasLeft) release();
            if (left) drag(mx, my);
        }
        wasLeft = left;
        wasRight = right;
    }

    private boolean inSearch(int mx, int my) {
        return mx >= searchX && mx < searchX + SEARCH_W && my >= searchY && my < searchY + 24;
    }

    private boolean inClose(int mx, int my) {
        float dx = mx - (winX + winW - 26), dy = my - (winY + 23);
        return dx * dx + dy * dy <= 144f;
    }

    private void press(int mx, int my, int button) {
        if (typing != null) {            // клик в другом месте применяет введённое число
            typing.stopEditing(true);
            typing = null;
        }
        if (button == 0 && inSearch(mx, my)) {
            searchFocus = true;
            return;
        }
        searchFocus = false;

        if (button == 0 && inClose(mx, my)) {
            onClose();
            return;
        }

        // категории
        int sx = Math.round(winX) + 8, sy = Math.round(winY) + 8;
        Category[] cats = Category.values();
        for (int i = 0; i < cats.length; i++) {
            int ty = sy + 44 + i * 30;
            if (mx >= sx + 6 && mx < sx + SB_W - 14 && my >= ty && my < ty + 26) {
                if (button == 0) selectCategory(cats[i]);
                return;
            }
        }

        // карточки
        if (mx >= vx && mx < vx + vw && my >= vy && my < vy + vh) {
            for (Module m : shown) {
                if (!m.contains(mx, my)) continue;
                GuiComponent c = m.mouseDown(mx, my, button);
                if (c != null) {
                    active = c;
                    if (c.isEditing()) typing = c;
                }
                return;
            }
            return;
        }

        // перетаскивание окна: верхняя полоса и пустые места
        if (button == 0 && mx >= winX && mx < winX + winW && my >= winY && my < winY + TOP_H) {
            draggingWin = true;
            grabX = mx - winX;
            grabY = my - winY;
        }
    }

    private void drag(int mx, int my) {
        if (draggingWin) {
            winX = mx - grabX;
            winY = my - grabY;
            clampWindow();
        } else if (active != null) {
            active.mouseDrag(mx, my);
        }
    }

    private void release() {
        boolean save = false;
        if (active != null) {
            active.mouseUp();
            active = null;
            save = true;
        }
        if (draggingWin) {
            draggingWin = false;
            Config c = Config.get();
            c.guiX = Math.round(winX);
            c.guiY = Math.round(winY);
            save = true;
        }
        if (save) {
            Config.save();
            BaseFinderConfig.save();
        }
    }

    private void selectCategory(Category c) {
        if (c == category && qLower.isEmpty()) return;
        category = c;
        Config.get().guiCategory = c.ordinal();
        query = "";
        qLower = "";
        queryView = "";
        scrollTarget = 0f;
        scrollCur = 0f;
        catFade = 0f;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollTarget -= (float) scrollY * 46f;
        return true;
    }

    // ------------------------------------------------------------------ ввод с клавиатуры

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (PortihClient.openMenu != null && PortihClient.openMenu.matches(event)
                && System.currentTimeMillis() - openedAt > 250L && typing == null && !searchFocus) {
            onClose();
            return true;
        }
        if (typing != null) {
            typing.keyPressed(key);
            if (!typing.isEditing()) typing = null;
            return true;
        }
        if (searchFocus) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!query.isEmpty()) setQuery(query.substring(0, query.length() - 1));
            } else if (key == GLFW.GLFW_KEY_ESCAPE) {
                if (!query.isEmpty()) setQuery("");
                else searchFocus = false;
            } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                searchFocus = false;
            }
            return true;
        }
        return super.keyPressed(event);   // Esc -> onClose()
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        int cp = event.codepoint();
        if (typing != null) return typing.charTyped(cp);
        if (searchFocus) {
            if (cp >= 32 && cp != 127 && query.length() < 28) setQuery(query + new String(Character.toChars(cp)));
            return true;
        }
        return false;
    }

    private void setQuery(String q) {
        query = q;
        qLower = q.trim().toLowerCase(Locale.ROOT);
        String v = q;
        int maxW = SEARCH_W - 40;
        while (v.length() > 1 && this.font.width(v) > maxW) v = v.substring(1);   // показываем хвост
        queryView = v;
        scrollTarget = 0f;
        scrollCur = 0f;
    }

    // ------------------------------------------------------------------ рисование

    private void drawWindow(int mx, int my) {
        int acc = RenderUtil.accent();
        int wx = Math.round(winX), wy = Math.round(winY);

        Draw.shadow(RenderUtil.g, wx, wy, winW, winH, 12, 0.9f * openAnim);
        RenderUtil.rect(wx, wy, winW, winH, 12, RenderUtil.bg(RenderUtil.BG_WINDOW, 0.96f));
        RenderUtil.outline(wx, wy, winW, winH, 12, RenderUtil.OUTLINE);

        drawSidebar(mx, my, wx, wy, acc);
        drawTopBar(mx, my, wx, wy, acc);
        drawContent(mx, my);
    }

    private void drawSidebar(int mx, int my, int wx, int wy, int acc) {
        int sx = wx + 8, sy = wy + 8, sw = SB_W - 8, sh = winH - 16;
        RenderUtil.rect(sx, sy, sw, sh, 10, RenderUtil.bg(RenderUtil.BG_SIDEBAR, 0.96f));
        RenderUtil.outline(sx, sy, sw, sh, 10, RenderUtil.OUTLINE);

        RenderUtil.star(sx + 15, sy + 15, 6, acc);
        RenderUtil.bold("PortihMod", sx + 26, sy + 11, RenderUtil.TEXT);
        RenderUtil.small("ClickGUI", sx + 26, sy + 23, RenderUtil.MUTED);

        Category[] cats = Category.values();
        int top = sy + 44;
        float want = top + category.ordinal() * 30;
        tabSel = tabSel < 0f ? want : AnimationUtil.approach(tabSel, want, dt, 16f);
        float selA = qLower.isEmpty() ? 1f : 0.3f;    // при поиске категория "приглушена"
        RenderUtil.rect(sx + 6, tabSel, sw - 12, 26, 8, Draw.fade(acc, 0.16f * selA));
        RenderUtil.rect(sx + 6, tabSel + 7, 3, 12, 2, Draw.fade(acc, selA));

        for (int i = 0; i < cats.length; i++) {
            int ty = top + i * 30;
            boolean hov = mx >= sx + 6 && mx < sx + sw - 6 && my >= ty && my < ty + 26;
            catHover[i] = AnimationUtil.approach(catHover[i], hov ? 1f : 0f, dt, 16f);
            boolean sel = cats[i] == category && qLower.isEmpty();
            float k = Math.max(sel ? 1f : 0f, catHover[i]);
            RenderUtil.icon(cats[i], sx + 24, ty + 13, Draw.lerpColor(RenderUtil.MUTED, sel ? acc : RenderUtil.TEXT, k),
                    RenderUtil.BG_SIDEBAR);
            int tc = Draw.lerpColor(RenderUtil.MUTED, RenderUtil.TEXT, k);
            if (sel) RenderUtil.bold(cats[i].title, sx + 40, ty + 9, tc);
            else RenderUtil.text(cats[i].title, sx + 40, ty + 9, tc);
        }
        int fy = sy + sh - 34;
        RenderUtil.rect(sx + 6, fy, sw - 12, 28, 9, Draw.fade(RenderUtil.WHITE, 0.04f));
        RenderUtil.circle(sx + 20, fy + 14, 9, Draw.fade(acc, 0.22f));
        RenderUtil.star(sx + 20, fy + 14, 4, acc);
        RenderUtil.text("PortihMod", sx + 32, fy + 7, RenderUtil.TEXT);
        RenderUtil.small("Клиентский мод  -  " + closeKeyName, sx + 32, fy + 18, RenderUtil.MUTED);
    }

    private void drawTopBar(int mx, int my, int wx, int wy, int acc) {
        // заголовок: жирный
        RenderUtil.boldScaled(qLower.isEmpty() ? category.title : "Поиск", vx + 2, wy + 15, RenderUtil.TEXT, 1.2f);

        // строка поиска
        searchAnim = AnimationUtil.approach(searchAnim, searchFocus ? 1f : 0f, dt, 16f);
        RenderUtil.rect(searchX, searchY, SEARCH_W, 24, 9, RenderUtil.BG_FIELD);
        RenderUtil.outline(searchX, searchY, SEARCH_W, 24, 9, Draw.lerpColor(RenderUtil.OUTLINE, acc, searchAnim));
        int ic = Draw.lerpColor(RenderUtil.MUTED, acc, searchAnim);
        RenderUtil.circle(searchX + 13, searchY + 11, 5, ic);          // лупа: кольцо + ручка
        RenderUtil.circle(searchX + 13, searchY + 11, 3, RenderUtil.BG_FIELD);
        RenderUtil.line(searchX + 17, searchY + 15, searchX + 21, searchY + 19, 2f, ic);
        if (query.isEmpty() && !searchFocus) {
            RenderUtil.text("Поиск модулей...", searchX + 28, searchY + 8, RenderUtil.MUTED);
        } else {
            RenderUtil.text(queryView, searchX + 28, searchY + 8, RenderUtil.TEXT);
            if (searchFocus && (System.currentTimeMillis() / 450L) % 2L == 0L) {
                int cx = searchX + 28 + RenderUtil.width(queryView);
                RenderUtil.fill(cx, searchY + 6, cx + 1, searchY + 18, acc);
            }
        }

        // кнопка закрытия
        closeHover = AnimationUtil.approach(closeHover, inClose(mx, my) ? 1f : 0f, dt, 16f);
        float ccx = winX + winW - 26, ccy = winY + 23;
        RenderUtil.circle(ccx, ccy, 11, Draw.fade(RenderUtil.WHITE, 0.07f * closeHover));
        int xc = Draw.lerpColor(RenderUtil.MUTED, RenderUtil.TEXT, closeHover);
        RenderUtil.line(ccx - 4, ccy - 4, ccx + 4, ccy + 4, 2f, xc);
        RenderUtil.line(ccx - 4, ccy + 4, ccx + 4, ccy - 4, 2f, xc);
    }

    private void drawContent(int mx, int my) {
        int acc = RenderUtil.accent();
        float oldAlpha = RenderUtil.alpha;
        RenderUtil.alpha = oldAlpha * catFade;
        RenderUtil.clipTop = vy;
        RenderUtil.clipBottom = vy + vh;

        RenderUtil.g.enableScissor(vx, vy, vx + vw, vy + vh);
        if (shown.isEmpty()) {
            String s = "Ничего не найдено";
            RenderUtil.text(s, vx + (vw - RenderUtil.width(s)) / 2f, vy + vh / 2f - 4, RenderUtil.MUTED);
        }
        for (Module m : shown) {
            float top = m.top();
            if (top > vy + vh + 4 || top + m.height() < vy - 4) continue;   // за пределами видимости
            m.draw(mx, my, dt);
        }
        RenderUtil.g.disableScissor();

        // полоса прокрутки
        if (contentH > vh) {
            float trackH = vh;
            float thumbH = Math.max(24f, trackH * vh / contentH);
            float maxScroll = contentH - vh;
            float ty = vy + (trackH - thumbH) * (maxScroll <= 0 ? 0f : scrollCur / maxScroll);
            RenderUtil.rect(vx + vw - 3, vy, 3, trackH, 1, Draw.fade(RenderUtil.WHITE, 0.05f));
            RenderUtil.rect(vx + vw - 3, ty, 3, thumbH, 1, Draw.fade(acc, 0.7f));
        }
        RenderUtil.alpha = oldAlpha;
        RenderUtil.clipTop = 0;
        RenderUtil.clipBottom = Integer.MAX_VALUE;
    }
}
