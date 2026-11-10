import java.awt.*; import java.awt.geom.*; import java.awt.image.BufferedImage; import java.io.File; import javax.imageio.ImageIO;
/** Draws Shelf's original app icon (square) and TV banner: a glossy violet-blue tile with flowing light lines. */
public class AppIcon {
  static void ribbons(Graphics2D g, int w, int h, float y0, float amp, float stroke) {
    for (int k = 0; k < 7; k++) {
      Path2D p = new Path2D.Float();
      for (int x = 0; x <= w; x += 2) {
        double y = h * y0 + amp * Math.sin(x * 2 * Math.PI / (w * 1.1) + 0.6 + k * 0.12) + k * stroke * 2.2;
        if (x == 0) p.moveTo(x, y); else p.lineTo(x, y);
      }
      float mid = 1f - Math.abs(k - 3) / 3.5f;
      g.setColor(new Color(1f, 1f, 1f, 0.18f + 0.55f * mid));
      g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.draw(p);
    }
  }
  static BufferedImage tile(int w, int h, float corner, String word, Font font) {
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = img.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    Shape clip = new RoundRectangle2D.Float(0, 0, w, h, corner, corner);
    g.setClip(clip);
    g.setPaint(new GradientPaint(0, 0, new Color(0x3b2a9e), w, h, new Color(0x0b1840)));
    g.fill(clip);
    g.setPaint(new RadialGradientPaint(w * 0.2f, h * 0.15f, w * 0.7f, new float[]{0f, 1f},
        new Color[]{new Color(120, 160, 255, 110), new Color(120, 160, 255, 0)}));
    g.fill(clip);
    ribbons(g, w, h, word == null ? 0.52f : 0.62f, h * 0.09f, Math.max(1.5f, h / 90f));
    if (word != null) {
      g.setColor(Color.WHITE); g.setFont(font.deriveFont(h * 0.30f));
      g.drawString(word, w * 0.08f, h * 0.44f);
    }
    // glossy top sheen + rim
    g.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 70), 0, h * 0.45f, new Color(255, 255, 255, 0)));
    g.fill(new Ellipse2D.Float(-w * 0.3f, -h * 0.75f, w * 1.6f, h * 1.2f));
    g.setClip(null);
    g.setColor(new Color(255, 255, 255, 90)); g.setStroke(new BasicStroke(Math.max(1f, h / 120f)));
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
