import java.awt.*; import java.awt.geom.*; import java.awt.image.BufferedImage; import java.io.File; import javax.imageio.ImageIO;
/** Draws Shelf's original app icon (square) and TV banner: a dark glossy tile with three app tiles standing on a thin bright shelf line. */
public class AppIcon {
  /** Three app tiles standing on a thin bright shelf line. */
  static void shelf(Graphics2D g, float x, float y, float s) {
    float lineY = y + s * 0.70f, lineT = s * 0.04f;
    // soft light under the shelf line
    g.setPaint(new RadialGradientPaint(x + s * 0.5f, lineY, s * 0.42f, new float[]{0f, 1f},
        new Color[]{new Color(150, 170, 255, 80), new Color(150, 170, 255, 0)}));
    g.fill(new Rectangle2D.Float(x, lineY - s * 0.42f, s, s * 0.84f));
    float[][] t = {{0.20f, 0.17f, 0.17f}, {0.40f, 0.24f, 0.24f}, {0.70f, 0.14f, 0.14f}}; // x, w, h as fractions
    Color[][] c = {{new Color(0x5ED0FF), new Color(0x2F6BFF)}, {new Color(0xC3A4FF), new Color(0x7A4DFF)}, {new Color(0xFFA27A), new Color(0xF2542D)}};
    for (int i = 0; i < 3; i++) {
      float tw = s * t[i][1], th = s * t[i][2], tx = x + s * t[i][0] - (i == 2 ? s * 0.04f : 0), ty = lineY - th - lineT * 0.5f;
      RoundRectangle2D r = new RoundRectangle2D.Float(tx, ty, tw, th, tw * 0.26f, tw * 0.26f);
      g.setPaint(new GradientPaint(tx, ty, c[i][0], tx, ty + th, c[i][1])); g.fill(r);
      g.setPaint(new GradientPaint(tx, ty, new Color(255, 255, 255, 110), tx, ty + th * 0.5f, new Color(255, 255, 255, 0)));
      g.fill(new RoundRectangle2D.Float(tx, ty, tw, th * 0.55f, tw * 0.26f, tw * 0.26f));
    }
    g.setColor(Color.WHITE); g.setStroke(new BasicStroke(lineT, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.draw(new Line2D.Float(x + s * 0.14f, lineY, x + s * 0.86f, lineY));
  }
  static BufferedImage tile(int w, int h, float corner, String word, Font font) {
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = img.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    Shape clip = new RoundRectangle2D.Float(0, 0, w, h, corner, corner);
    g.setClip(clip);
    g.setPaint(new GradientPaint(0, 0, new Color(0x161C30), w, h, new Color(0x05070D)));
    g.fill(clip);
    g.setPaint(new RadialGradientPaint(w * 0.3f, h * 0.0f, Math.max(w, h) * 0.8f, new float[]{0f, 1f},
        new Color[]{new Color(110, 140, 255, 90), new Color(110, 140, 255, 0)}));
    g.fill(clip);
    if (word == null) {
      shelf(g, 0, -Math.min(w, h) * 0.06f, Math.min(w, h));
    } else {
      float s = h * 0.86f;
      shelf(g, h * 0.04f, -h * 0.03f, s);
      g.setColor(Color.WHITE); g.setFont(font.deriveFont(h * 0.27f));
      g.drawString(word, h * 0.98f, h * 0.58f);
    }
    // glossy top sheen + rim
    g.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 46), 0, h * 0.45f, new Color(255, 255, 255, 0)));
    g.fill(new Ellipse2D.Float(-w * 0.3f, -h * 0.75f, w * 1.6f, h * 1.2f));
    g.setClip(null);
    g.setColor(new Color(255, 255, 255, 70)); g.setStroke(new BasicStroke(Math.max(1f, h / 120f)));
    g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, corner, corner));
    g.dispose();
    return img;
  }
  public static void main(String[] a) throws Exception {
    Font light = Font.createFont(Font.TRUETYPE_FONT, new File(a[0]));
    File res = new File(a[1]);
    int[][] mip = {{48, 1}, {72, 2}, {96, 3}, {144, 4}, {192, 5}};
    String[] dirs = {"mipmap-mdpi", "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi"};
    for (int i = 0; i < mip.length; i++) {
      File d = new File(res, dirs[i]); d.mkdirs();
      int s = mip[i][0];
      ImageIO.write(tile(s, s, s * 0.46f, null, light), "png", new File(d, "ic_launcher.png"));
    }
    ImageIO.write(tile(320, 180, 24, "Shelf", light), "png", new File(res, "drawable-xhdpi/banner_app.png"));
  }
}
