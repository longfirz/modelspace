package com.example.modelspace.client.editor;

import com.example.modelspace.ModBlocks;
import com.example.modelspace.ModItems;
import com.example.modelspace.client.EditorSession;
import com.example.modelspace.client.EditorState;
import com.example.modelspace.client.FreecamController;
import com.example.modelspace.client.ModelSpaceKeys;
import com.example.modelspace.model.ModelPart;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Экран редактора. Мышь свободна: ЛКМ — выбор/гизмо/интерфейс, ПКМ+перетаскивание — взгляд,
 * СКМ+перетаскивание — панорама, колесо — зум (Ctrl+колесо — скорость полёта).
 * Мир при этом не ставится на паузу, камеру двигает FreecamController.
 */
public final class EditorScreen extends Screen {
	private static final int BG = 0xC0101418;
	private static final int ACCENT = 0xFF5AC8FF;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int DIM = 0xFFAAAAAA;
	private static final int BTN = 0xFF2A323A;
	private static final int BTN_HOT = 0xFF3B4650;
	private static final int BTN_ON = 0xFF1F6F99;
	private static final int FIELD_BG = 0xFF0B0E11;
	private static final int ROW_SEL = 0xFF1F4F6A;
	private static final int ROW_PRIMARY = 0xFF2B7BA8;

	private static final int LEFT_W = 150;
	private static final int RIGHT_W = 138;
	private static final int ROW_H = 12;
	private static final int MARGIN = 6;
	private static final int FIELD_H = 13;
	private static final int ID_BOX_H = 14;

	private static final class Btn {
		final Supplier<Component> label;
		final Runnable action;
		final BooleanSupplier active;
		final BooleanSupplier enabled;
		int x, y, w, h;

		Btn(Supplier<Component> label, Runnable action, BooleanSupplier active, BooleanSupplier enabled) {
			this.label = label;
			this.action = action;
			this.active = active;
			this.enabled = enabled;
		}

		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private final List<Btn> buttons = new ArrayList<>();
	private final Gizmo gizmo = new Gizmo();
	private Viewport vp;

	// раскладка (пересчитывается каждый кадр)
	private int toolbarBottom = 22;
	private int listTop, listBottom, idBoxTop;
	private int listScroll;

	// ввод
	private boolean fwd, back, left, right, up, down, sprint;
	private boolean ctrlHeld;
	private boolean lookDrag, panDrag, gizmoDrag;
	private ModelDocument.Snapshot dragBefore;
	private boolean clickShift;
	private int lastClickRow = -1;
	private long lastClickTime;

	// текстовый ввод
	private int fGroup = -1;
	private int fAxis = -1;
	private final StringBuilder fBuf = new StringBuilder();
	private boolean fFresh;
	private boolean idFocus;
	private final StringBuilder idBuf = new StringBuilder();

	/** 26.3 (SDL): о начале/конце набора текста нужно сообщать игре, иначе charTyped не приходит. */
	private boolean textInputActive;

	private Component status = Component.empty();
	private long statusUntil;

	public EditorScreen() {
		super(Component.translatable("modelspace.hud.title"));
		buildButtons();
	}

	// ====================================================================== вспомогательное

	private static Font font() {
		return Minecraft.getInstance().font;
	}

	private static ModelDocument doc() {
		return EditorState.doc;
	}

	private void text(GuiGraphicsExtractor g, Component c, int x, int y, int color) {
		g.text(font(), c, x, y, color, true);
	}

	private void text(GuiGraphicsExtractor g, String s, int x, int y, int color) {
		text(g, Component.literal(s), x, y, color);
	}

	private void setStatus(String key) {
		status = Component.translatable(key);
		statusUntil = System.currentTimeMillis() + 3000L;
	}

	private static String fmt(float v) {
		String s = String.format(Locale.ROOT, "%.3f", v);
		if (s.indexOf('.') >= 0) {
			s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
		}
		return s.equals("-0") ? "0" : s;
	}

	private static String fit(String s, int maxWidth) {
		Font f = font();
		while (s.length() > 1 && f.width(Component.literal(s)) > maxWidth) {
			s = s.substring(0, s.length() - 1);
		}
		return s;
	}

	// ====================================================================== кнопки

	private void btn(String key, Runnable action, BooleanSupplier active) {
		buttons.add(new Btn(() -> Component.translatable(key), action, active, () -> true));
	}

	private void buildButtons() {
		btn("modelspace.tool.move", () -> EditorState.tool = EditorState.Tool.MOVE, () -> EditorState.tool == EditorState.Tool.MOVE);
		btn("modelspace.tool.rotate", () -> EditorState.tool = EditorState.Tool.ROTATE, () -> EditorState.tool == EditorState.Tool.ROTATE);
		btn("modelspace.tool.scale", () -> EditorState.tool = EditorState.Tool.SCALE, () -> EditorState.tool == EditorState.Tool.SCALE);
		buttons.add(new Btn(
			() -> Component.translatable("modelspace.hud.space").append(": ").append(Component.translatable(
				EditorState.space == EditorState.Space.GLOBAL ? "modelspace.space.global" : "modelspace.space.local")),
			EditorState::toggleSpace, () -> false, () -> true));
		btn("modelspace.hud.pivot", EditorState::togglePivot, () -> EditorState.pivotMode);
		buttons.add(new Btn(
			() -> Component.translatable("modelspace.hud.mode").append(": ").append(Component.translatable(
				EditorState.mode == EditorState.Mode.NORMAL ? "modelspace.mode.normal" : "modelspace.mode.fine")),
			EditorState::toggleMode, () -> EditorState.mode == EditorState.Mode.FINE, () -> true));
		buttons.add(new Btn(() -> Component.translatable("modelspace.btn.undo"), () -> doc().undo(), () -> false, () -> doc().canUndo()));
		buttons.add(new Btn(() -> Component.translatable("modelspace.btn.redo"), () -> doc().redo(), () -> false, () -> doc().canRedo()));
		btn("modelspace.btn.add", this::addFromHand, () -> false);
		buttons.add(new Btn(() -> Component.translatable("modelspace.btn.delete"), () -> doc().deleteSelected(), () -> false, () -> doc().hasSelection()));
		buttons.add(new Btn(() -> Component.translatable("modelspace.btn.duplicate"), () -> doc().duplicateSelected(), () -> false, () -> doc().hasSelection()));
		for (int axis = 0; axis < 3; axis++) {
			final int a = axis;
			buttons.add(new Btn(
				() -> Component.translatable("modelspace.btn.mirror", "XYZ".substring(a, a + 1)),
				() -> mirror(a, clickShift), () -> false, () -> doc().hasSelection()));
		}
		buttons.add(new Btn(
			() -> Component.translatable("modelspace.btn.save").append(doc() != null && doc().isDirty() ? "*" : ""),
			() -> { EditorSession.save(); setStatus("modelspace.status.saved"); }, () -> false, () -> true));
		btn("modelspace.btn.exit", this::onClose, () -> false);
	}

	private void layoutToolbar() {
		int x = MARGIN;
		int y = 4;
		int rowH = 14;
		for (Btn b : buttons) {
			int w = font().width(b.label.get()) + 10;
			if (x + w > width - MARGIN && x > MARGIN) {
				x = MARGIN;
				y += rowH + 2;
			}
			b.x = x;
			b.y = y;
			b.w = w;
			b.h = rowH;
			x += w + 3;
		}
		toolbarBottom = y + rowH + 5;
	}

	// ====================================================================== операции

	private void mirror(int axis, boolean copy) {
		if (copy) {
			doc().mirrorCopy(axis);
		} else {
			doc().mirrorInPlace(axis);
		}
	}

	private void addPart(BlockState state) {
		if (state.isAir()) {
			return;
		}
		ModelPart part = new ModelPart(state);
		part.pos.set(0.0F, 1.0F, 0.0F); // сверху над блоком Workspace
		if (!doc().add(part)) {
			setStatus("modelspace.status.limit");
		}
	}

	private void addFromHand() {
		Player p = Minecraft.getInstance().player;
		if (p == null) {
			return;
		}
		ItemStack main = p.getMainHandItem();
		ItemStack src = main.is(ModItems.WORKSPACE_KEY) ? p.getOffhandItem() : main;
		if (src.getItem() instanceof BlockItem bi && bi.getBlock() != ModBlocks.WORKSPACE) {
			addPart(bi.getBlock().defaultBlockState());
		} else {
			setStatus("modelspace.status.no_block");
		}
	}

	private void addById() {
		String s = idBuf.toString().trim();
		idBuf.setLength(0);
		if (s.isEmpty()) {
			return;
		}
		Identifier id = Identifier.tryParse(s);
		Optional<Block> block = id == null ? Optional.empty() : BuiltInRegistries.BLOCK.getOptional(id);
		if (block.isPresent() && block.get() != ModBlocks.WORKSPACE) {
			addPart(block.get().defaultBlockState());
		} else {
			setStatus("modelspace.status.bad_id");
		}
	}

	private void focusSelection() {
		ModelPart p = doc().primary();
		if (p == null) {
			return;
		}
		Vector3f c = p.worldCenter();
		BlockPos a = EditorState.anchor;
		float maxScale = Math.max(p.scale.x, Math.max(p.scale.y, p.scale.z));
		FreecamController.focusOn(new Vec3(a.getX() + c.x, a.getY() + c.y, a.getZ() + c.z), Math.max(3.0, maxScale * 2.5 + 1.5));
	}

	// ====================================================================== числовые поля

	private void startField(int group, int axis) {
		ModelPart p = doc().primary();
		if (p == null) {
			return;
		}
		fGroup = group;
		fAxis = axis;
		fBuf.setLength(0);
		fBuf.append(fmt(ModelDocument.get(p.vec(group), axis)));
		fFresh = true;
		idFocus = false;
	}

	private void commitField() {
		if (fGroup < 0) {
			return;
		}
		int g = fGroup;
		int a = fAxis;
		fGroup = -1;
		fAxis = -1;
		try {
			float v = Float.parseFloat(fBuf.toString());
			if (Float.isFinite(v)) {
				doc().setComponent(g, a, v);
			}
		} catch (NumberFormatException ignored) {
			// мусор — просто отбрасываем
		}
	}

	private void cancelField() {
		fGroup = -1;
		fAxis = -1;
	}

	// ====================================================================== раскладка панелей

	private int leftX() { return MARGIN; }
	private int leftY() { return toolbarBottom + 4; }
	private int leftH() { return height - leftY() - MARGIN; }
	private int rightX() { return width - RIGHT_W - MARGIN; }
	private int rightY() { return toolbarBottom + 4; }
	private int rightH() { return 18 + 4 * 26 + 4; }

	private boolean overRight(double mx, double my) {
		return doc() != null && doc().hasSelection()
			&& mx >= rightX() && mx < rightX() + RIGHT_W && my >= rightY() && my < rightY() + rightH();
	}

	private boolean overLeft(double mx, double my) {
		return mx >= leftX() && mx < leftX() + LEFT_W && my >= leftY() && my < leftY() + leftH();
	}

	private boolean overUi(double mx, double my) {
		return my < toolbarBottom || overLeft(mx, my) || overRight(mx, my);
	}

	private int fieldX(int axis) {
		int cw = (RIGHT_W - 12) / 3;
		return rightX() + 6 + axis * cw;
	}

	private int fieldW() {
		return (RIGHT_W - 12) / 3 - 2;
	}

	private int fieldY(int group) {
		return rightY() + 18 + group * 26 + 10;
	}

	// ====================================================================== жизненный цикл

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void tick() {
		if (doc() == null || !FreecamController.isActive()) {
			onClose();
			return;
		}
		boolean typing = fGroup >= 0 || idFocus;
		syncTextInput(typing);
		if (typing) {
			FreecamController.clearInput();
		} else {
			FreecamController.setInput(fwd, back, left, right, up, down, sprint);
		}
	}

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(null);
    }

	private void syncTextInput(boolean typing) {
		if (typing != textInputActive) {
			textInputActive = typing;
			Minecraft.getInstance().onTextInputFocusChange(this, typing);
		}
	}

	@Override
	public void removed() {
		// Вызывается при любом закрытии/замене экрана — сессия не должна пережить экран
		syncTextInput(false);
		commitField();
		EditorSession.end(Minecraft.getInstance());
	}

	// ====================================================================== клавиатура

	@Override
	public boolean keyPressed(KeyEvent event) {
		ctrlHeld = event.hasControlDown();
		int key = event.key();
		ModelDocument doc = doc();
		if (doc == null) {
			return false;
		}

		if (key == InputConstants.KEY_ESCAPE) {
			if (fGroup >= 0) {
				cancelField();
			} else if (idFocus) {
				idFocus = false;
				idBuf.setLength(0);
			} else {
				onClose();
			}
			return true;
		}

		if (fGroup >= 0) {
			if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
				commitField();
			} else if (key == InputConstants.KEY_TAB) {
				int g = fGroup;
				int a = fAxis;
				commitField();
				int n = (g * 3 + a + 1) % 12;
				startField(n / 3, n % 3);
			} else if (key == InputConstants.KEY_BACKSPACE) {
				if (fFresh) {
					fBuf.setLength(0);
					fFresh = false;
				} else if (fBuf.length() > 0) {
					fBuf.setLength(fBuf.length() - 1);
				}
			}
			return true;
		}
		if (idFocus) {
			if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
				addById();
				idFocus = false;
			} else if (key == InputConstants.KEY_BACKSPACE && idBuf.length() > 0) {
				idBuf.setLength(idBuf.length() - 1);
			}
			return true;
		}

		boolean ctrl = event.hasControlDown();
		boolean shift = event.hasShiftDown();
		boolean alt = event.hasAltDown();

		if (ctrl) {
			switch (key) {
				case InputConstants.KEY_Z -> { if (shift) doc.redo(); else doc.undo(); return true; }
				case InputConstants.KEY_Y -> { doc.redo(); return true; }
				case InputConstants.KEY_C -> { doc.copyToClipboard(); return true; }
				case InputConstants.KEY_X -> { doc.copyToClipboard(); doc.deleteSelected(); return true; }
				case InputConstants.KEY_V -> { doc.paste(); return true; }
				case InputConstants.KEY_D -> { doc.duplicateSelected(); return true; }
				case InputConstants.KEY_A -> { doc.selectAll(); return true; }
				case InputConstants.KEY_S -> { EditorSession.save(); setStatus("modelspace.status.saved"); return true; }
				default -> { }
			}
		}
		if (key == InputConstants.KEY_DELETE) {
			doc.deleteSelected();
			return true;
		}
		if (ModelSpaceKeys.exit.matches(event)) {
			onClose();
			return true;
		}
		if (ModelSpaceKeys.toolMove.matches(event)) { EditorState.tool = EditorState.Tool.MOVE; return true; }
		if (ModelSpaceKeys.toolRotate.matches(event)) { EditorState.tool = EditorState.Tool.ROTATE; return true; }
		if (ModelSpaceKeys.toolScale.matches(event)) { EditorState.tool = EditorState.Tool.SCALE; return true; }
		if (ModelSpaceKeys.pivot.matches(event)) { EditorState.togglePivot(); return true; }
		if (ModelSpaceKeys.space.matches(event)) { EditorState.toggleSpace(); return true; }
		if (ModelSpaceKeys.mode.matches(event)) { EditorState.toggleMode(); return true; }
		if (ModelSpaceKeys.mirrorX.matches(event)) { mirror(0, alt); return true; }
		if (ModelSpaceKeys.mirrorY.matches(event)) { mirror(1, alt); return true; }
		if (ModelSpaceKeys.mirrorZ.matches(event)) { mirror(2, alt); return true; }

		// полёт
		Options o = Minecraft.getInstance().options;
		boolean handled = false;
		if (o.keyUp.matches(event)) { fwd = true; handled = true; }
		if (o.keyDown.matches(event)) { back = true; handled = true; }
		if (o.keyLeft.matches(event)) { left = true; handled = true; }
		if (o.keyRight.matches(event)) { right = true; handled = true; }
		if (o.keyJump.matches(event)) { up = true; handled = true; }
		if (o.keyShift.matches(event)) { down = true; handled = true; }
		if (o.keySprint.matches(event)) { sprint = true; handled = true; }
		return handled;
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		ctrlHeld = event.hasControlDown();
		Options o = Minecraft.getInstance().options;
		boolean handled = false;
		if (o.keyUp.matches(event)) { fwd = false; handled = true; }
		if (o.keyDown.matches(event)) { back = false; handled = true; }
		if (o.keyLeft.matches(event)) { left = false; handled = true; }
		if (o.keyRight.matches(event)) { right = false; handled = true; }
		if (o.keyJump.matches(event)) { up = false; handled = true; }
		if (o.keyShift.matches(event)) { down = false; handled = true; }
		if (o.keySprint.matches(event)) { sprint = false; handled = true; }
		return handled;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		int cp = event.codepoint();
		if (!Character.isValidCodePoint(cp) || cp > 0xFFFF) {
			return false;
		}
		char c = (char) cp;
		if (fGroup >= 0) {
			if (c == ',') {
				c = '.';
			}
			if ((c >= '0' && c <= '9') || c == '.' || c == '-') {
				if (fFresh) {
					fBuf.setLength(0);
					fFresh = false;
				}
				if (fBuf.length() < 12) {
					fBuf.append(c);
				}
			}
			return true;
		}
		if (idFocus) {
			c = Character.toLowerCase(c);
			if (((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == ':' || c == '.' || c == '/' || c == '-')
					&& idBuf.length() < 64) {
				idBuf.append(c);
			}
			return true;
		}
		return false;
	}

	// ====================================================================== мышь

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		ModelDocument doc = doc();
		if (doc == null) {
			return false;
		}
		double mx = event.x();
		double my = event.y();
		int button = event.button();
		clickShift = event.hasShiftDown();
		boolean shift = event.hasShiftDown();
		boolean ctrl = event.hasControlDown();

		commitField();
		idFocus = false;

		if (button == InputConstants.MOUSE_BUTTON_LEFT) {
			layoutToolbar();
			if (my < toolbarBottom) {
				for (Btn b : buttons) {
					if (b.contains(mx, my)) {
						if (b.enabled.getAsBoolean()) {
							b.action.run();
						}
						return true;
					}
				}
				return true;
			}
			if (overLeft(mx, my)) {
				clickLeft(mx, my, shift, ctrl);
				return true;
			}
			if (overRight(mx, my)) {
				clickRight(mx, my);
				return true;
			}
			if (vp == null) {
				return true;
			}
			int h = gizmo.pick(vp, mx, my);
			if (h >= 0) {
				ModelDocument.Snapshot before = doc.snapshot();
				if (gizmo.begin(h, vp, mx, my, doc)) {
					dragBefore = before;
					gizmoDrag = true;
					return true;
				}
			}
			doc.clickViewport(pickPart(doc, mx, my), shift || ctrl);
			return true;
		}
		if (overUi(mx, my)) {
			return true;
		}
		if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
			lookDrag = true;
			return true;
		}
		if (button == InputConstants.MOUSE_BUTTON_MIDDLE) {
			panDrag = true;
			return true;
		}
		return false;
	}

	private void clickLeft(double mx, double my, boolean shift, boolean ctrl) {
		ModelDocument doc = doc();
		if (my >= idBoxTop && my < idBoxTop + ID_BOX_H) {
			idFocus = true;
			return;
		}
		if (my >= listTop && my < listBottom) {
			int i = listScroll + (int) ((my - listTop) / ROW_H);
			if (i >= 0 && i < doc.parts().size()) {
				if (mx >= leftX() + LEFT_W - 18) {
					doc.toggleVisible(i);
					return;
				}
				long now = System.currentTimeMillis();
				boolean dbl = i == lastClickRow && now - lastClickTime < 350L;
				lastClickRow = i;
				lastClickTime = now;
				doc.clickList(i, shift, ctrl);
				if (dbl) {
					focusSelection();
				}
			}
		}
	}

	private void clickRight(double mx, double my) {
		for (int g = 0; g < 4; g++) {
			int fy = fieldY(g);
			if (my >= fy && my < fy + FIELD_H) {
				for (int a = 0; a < 3; a++) {
					if (mx >= fieldX(a) && mx < fieldX(a) + fieldW()) {
						startField(g, a);
						return;
					}
				}
			}
		}
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		int button = event.button();
		if (gizmoDrag && button == InputConstants.MOUSE_BUTTON_LEFT && vp != null && doc() != null) {
			gizmo.drag(vp, event.x(), event.y(), doc());
			return true;
		}
		if (lookDrag && button == InputConstants.MOUSE_BUTTON_RIGHT) {
			FreecamController.applyMouseDelta(dragX * 2.0, dragY * 2.0);
			return true;
		}
		if (panDrag && button == InputConstants.MOUSE_BUTTON_MIDDLE) {
			FreecamController.pan(dragX, dragY);
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		int button = event.button();
		if (gizmoDrag && button == InputConstants.MOUSE_BUTTON_LEFT) {
			if (gizmo.changed && doc() != null && dragBefore != null) {
				doc().commitDrag(dragBefore);
			}
			gizmo.end();
			gizmoDrag = false;
			dragBefore = null;
			return true;
		}
		if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
			lookDrag = false;
		}
		if (button == InputConstants.MOUSE_BUTTON_MIDDLE) {
			panDrag = false;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		ModelDocument doc = doc();
		if (doc == null || scrollY == 0.0) {
			return false;
		}
		if (overLeft(mx, my)) {
			int rows = Math.max(1, (listBottom - listTop) / ROW_H);
			int max = Math.max(0, doc.parts().size() - rows);
			listScroll = Math.max(0, Math.min(max, listScroll - (int) Math.signum(scrollY) * 2));
			return true;
		}
		if (overRight(mx, my)) {
			for (int g = 0; g < 4; g++) {
				int fy = fieldY(g);
				if (my >= fy && my < fy + FIELD_H) {
					for (int a = 0; a < 3; a++) {
						if (mx >= fieldX(a) && mx < fieldX(a) + fieldW()) {
							boolean fine = EditorState.mode == EditorState.Mode.FINE;
							float step = g == 1 ? (fine ? 1.0F : 15.0F) : (fine ? 0.02F : 0.0625F);
							doc.nudgeComponent(g, a, (float) Math.signum(scrollY) * step);
							return true;
						}
					}
				}
			}
			return true;
		}
		if (my < toolbarBottom) {
			return true;
		}
		if (ctrlHeld) {
			FreecamController.adjustSpeed(scrollY);
		} else {
			FreecamController.dolly(scrollY);
		}
		return true;
	}

	// ====================================================================== выбор части лучом

	private int pickPart(ModelDocument doc, double mx, double my) {
		Vector3d o = vp.cam;
		Vector3d d = vp.rayDir(mx, my);
		int best = -1;
		double bestT = Double.MAX_VALUE;
		List<ModelPart> parts = doc.parts();
		for (int i = 0; i < parts.size(); i++) {
			ModelPart p = parts.get(i);
			if (!p.visible) {
				continue;
			}
			Matrix4f inv = p.matrix().invert();
			Vector3f lo = inv.transformPosition((float) o.x, (float) o.y, (float) o.z, new Vector3f());
			Vector3f ld = inv.transformDirection((float) d.x, (float) d.y, (float) d.z, new Vector3f());
			double t = rayUnitCube(lo, ld);
			if (t >= 0.0 && t < bestT) {
				bestT = t;
				best = i;
			}
		}
		return best;
	}

	/** Пересечение луча с кубом [0,1]^3; возвращает параметр t или -1. */
	private static double rayUnitCube(Vector3f o, Vector3f d) {
		double tMin = -Double.MAX_VALUE;
		double tMax = Double.MAX_VALUE;
		for (int a = 0; a < 3; a++) {
			double oa = ModelDocument.get(o, a);
			double da = ModelDocument.get(d, a);
			if (Math.abs(da) < 1e-9) {
				if (oa < 0.0 || oa > 1.0) {
					return -1.0;
				}
			} else {
				double t1 = (0.0 - oa) / da;
				double t2 = (1.0 - oa) / da;
				tMin = Math.max(tMin, Math.min(t1, t2));
				tMax = Math.min(tMax, Math.max(t1, t2));
			}
		}
		if (tMax < Math.max(tMin, 0.0)) {
			return -1.0;
		}
		return tMin >= 0.0 ? tMin : tMax;
	}

	// ====================================================================== отрисовка

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		ModelDocument doc = doc();
		if (doc == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		layoutToolbar();

		Vec3 c = FreecamController.interpolatedPos(partialTick);
		BlockPos a = EditorState.anchor;
		vp = new Viewport(c.x - a.getX(), c.y - a.getY(), c.z - a.getZ(),
			FreecamController.yaw(), FreecamController.pitch(), mc.options.fov().get(), width, height);

		drawSelection(g, doc);
		gizmo.update(doc, vp);
		gizmo.hover = (!gizmoDrag && !overUi(mouseX, mouseY) && !lookDrag && !panDrag) ? gizmo.pick(vp, mouseX, mouseY) : -1;
		gizmo.draw(g, vp, doc);

		drawToolbar(g, mouseX, mouseY);
		drawLeft(g, doc);
		drawRight(g, doc);
		drawFooter(g);
	}

	private void drawSelection(GuiGraphicsExtractor g, ModelDocument doc) {
		int drawn = 0;
		for (int idx : doc.selection()) {
			if (drawn++ >= 64) {
				break;
			}
			ModelPart p = doc.parts().get(idx);
			Matrix4f m = p.matrix();
			double[][] c = new double[8][];
			for (int k = 0; k < 8; k++) {
				Vector3f v = m.transformPosition(k & 1, (k >> 1) & 1, (k >> 2) & 1, new Vector3f());
				c[k] = vp.project(v.x, v.y, v.z);
			}
			for (int k = 0; k < 8; k++) {
				for (int bit = 1; bit <= 4; bit <<= 1) {
					if ((k & bit) == 0) {
						DrawUtil.line(g, c[k], c[k | bit], ACCENT, 1, vp.width, vp.height);
					}
				}
			}
			if (EditorState.pivotMode && idx == doc.selection().iterator().next()) {
				Vector3f wp = p.worldPivot();
				double[] s = vp.project(wp.x, wp.y, wp.z);
				if (s != null) {
					DrawUtil.line(g, s[0] - 5, s[1], s[0] + 5, s[1], 0xFFFF9BFF, 1, vp.width, vp.height);
					DrawUtil.line(g, s[0], s[1] - 5, s[0], s[1] + 5, 0xFFFF9BFF, 1, vp.width, vp.height);
				}
			}
		}
	}

	private void drawToolbar(GuiGraphicsExtractor g, int mx, int my) {
		g.fill(0, 0, width, toolbarBottom, BG);
		g.fill(0, toolbarBottom - 1, width, toolbarBottom, ACCENT);
		for (Btn b : buttons) {
			boolean enabled = b.enabled.getAsBoolean();
			int color = b.active.getAsBoolean() ? BTN_ON : (enabled && b.contains(mx, my) ? BTN_HOT : BTN);
			g.fill(b.x, b.y, b.x + b.w, b.y + b.h, color);
			Component label = b.label.get();
			text(g, label, b.x + (b.w - font().width(label)) / 2, b.y + (b.h - 8) / 2 + 1, enabled ? TEXT : 0xFF666666);
		}
	}

	private void drawLeft(GuiGraphicsExtractor g, ModelDocument doc) {
		int x = leftX();
		int y = leftY();
		int h = leftH();
		g.fill(x, y, x + LEFT_W, y + h, BG);
		g.fill(x, y, x + LEFT_W, y + 1, ACCENT);
		text(g, Component.translatable("modelspace.panel.parts").append(" (" + doc.parts().size() + ")"), x + 6, y + 5, ACCENT);

		listTop = y + 16;
		idBoxTop = y + h - ID_BOX_H - 4;
		listBottom = idBoxTop - 4;
		int rows = Math.max(1, (listBottom - listTop) / ROW_H);
		int max = Math.max(0, doc.parts().size() - rows);
		listScroll = Math.max(0, Math.min(max, listScroll));

		Integer primaryIdx = doc.hasSelection() ? doc.selection().iterator().next() : null;
		for (int r = 0; r < rows; r++) {
			int i = listScroll + r;
			if (i >= doc.parts().size()) {
				break;
			}
			ModelPart p = doc.parts().get(i);
			int ry = listTop + r * ROW_H;
			if (doc.isSelected(i)) {
				g.fill(x + 2, ry, x + LEFT_W - 2, ry + ROW_H - 1, primaryIdx != null && primaryIdx == i ? ROW_PRIMARY : ROW_SEL);
			}
			int swatch = 0xFF000000 | p.state.getBlock().defaultMapColor().col;
			g.fill(x + 5, ry + 2, x + 12, ry + 9, swatch);
			String name = "#" + (i + 1) + " " + p.state.getBlock().getName().getString();
			text(g, fit(name, LEFT_W - 40), x + 16, ry + 2, p.visible ? TEXT : 0xFF777777);
			// иконка видимости
			int ex = x + LEFT_W - 14;
			g.fill(ex, ry + 2, ex + 8, ry + 9, p.visible ? ACCENT : 0xFF444444);
			if (!p.visible) {
				g.fill(ex + 1, ry + 3, ex + 7, ry + 8, BG);
			}
		}
		if (max > 0) {
			int trackH = listBottom - listTop;
			int barH = Math.max(8, trackH * rows / doc.parts().size());
			int barY = listTop + (trackH - barH) * listScroll / max;
			g.fill(x + LEFT_W - 3, barY, x + LEFT_W - 1, barY + barH, 0xFF556670);
		}

		// поле ввода id блока
		g.fill(x + 4, idBoxTop, x + LEFT_W - 4, idBoxTop + ID_BOX_H, idFocus ? ACCENT : 0xFF3A444C);
		g.fill(x + 5, idBoxTop + 1, x + LEFT_W - 5, idBoxTop + ID_BOX_H - 1, FIELD_BG);
		if (idFocus || idBuf.length() > 0) {
			text(g, fit(idBuf + (idFocus ? "_" : ""), LEFT_W - 16), x + 8, idBoxTop + 3, TEXT);
		} else {
			text(g, Component.translatable("modelspace.panel.id_hint"), x + 8, idBoxTop + 3, 0xFF667077);
		}
	}

	private void drawRight(GuiGraphicsExtractor g, ModelDocument doc) {
		if (!doc.hasSelection()) {
			return;
		}
		ModelPart p = doc.primary();
		int x = rightX();
		int y = rightY();
		g.fill(x, y, x + RIGHT_W, y + rightH(), BG);
		g.fill(x, y, x + RIGHT_W, y + 1, ACCENT);
		Component title = Component.translatable("modelspace.panel.selected");
		if (doc.selection().size() > 1) {
			title = title.copy().append(" x" + doc.selection().size());
		}
		text(g, title, x + 6, y + 5, ACCENT);

		String[] keys = {"modelspace.field.pos", "modelspace.field.rot", "modelspace.field.scale", "modelspace.field.pivot"};
		for (int grp = 0; grp < 4; grp++) {
			int fy = fieldY(grp);
			text(g, Component.translatable(keys[grp]), x + 6, fy - 10, DIM);
			Vector3f v = p.vec(grp);
			for (int a = 0; a < 3; a++) {
				int fx = fieldX(a);
				boolean editing = fGroup == grp && fAxis == a;
				g.fill(fx, fy, fx + fieldW(), fy + FIELD_H, editing ? ACCENT : 0xFF3A444C);
				g.fill(fx + 1, fy + 1, fx + fieldW() - 1, fy + FIELD_H - 1, FIELD_BG);
				String s = editing ? fBuf + (fFresh ? "" : "_") : fmt(ModelDocument.get(v, a));
				// цветной маркер оси слева
				g.fill(fx + 1, fy + 1, fx + 2, fy + FIELD_H - 1, a == 0 ? 0xFFE64B4B : a == 1 ? 0xFF4BD264 : 0xFF4B8CFF);
				text(g, fit(s, fieldW() - 6), fx + 4, fy + 3, editing && fFresh ? ACCENT : TEXT);
			}
		}
	}

	private void drawFooter(GuiGraphicsExtractor g) {
		Component hint = Component.translatable("modelspace.hint.controls");
		int hw = font().width(hint);
		text(g, hint, (width - hw) / 2, height - 12, DIM);
		if (System.currentTimeMillis() < statusUntil) {
			int sw = font().width(status);
			text(g, status, (width - sw) / 2, height - 26, 0xFFFFD866);
		}
		text(g, Component.translatable("modelspace.hud.speed", String.format(Locale.ROOT, "%.2f", FreecamController.speedMult())),
			width - RIGHT_W - MARGIN, height - 12, DIM);
	}
}
