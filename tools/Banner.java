import java.awt.*; import java.awt.image.BufferedImage; import java.io.File; import javax.imageio.ImageIO;
public class Banner {
  public static void main(String[] a) throws Exception {
    Font light = Font.createFont(Font.TRUETYPE_FONT, new File(a[0]));
    String[][] specs = {{"banner_aero","0d5a92","1690a8","Aero"},{"banner_wave","1e2a78","3a1c6e","Wave"},{"banner_grid","1c2536","07090e","Grid"}};
    for (String[] s : specs) {
      BufferedImage img = new BufferedImage(320, 180, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g.setPaint(new GradientPaint(0, 0, new Color(Integer.parseInt(s[1], 16)), 320, 180, new Color(Integer.parseInt(s[2], 16))));
      g.fillRect(0, 0, 320, 180);
      g.setPaint(new GradientPaint(0, 0, new Color(255,255,255,70), 0, 80, new Color(255,255,255,0)));
      g.fillOval(-60, -120, 440, 200);
      g.setColor(new Color(255,255,255,190)); g.setFont(light.deriveFont(24f)); g.drawString("Shelf", 22, 112);
      g.setColor(Color.WHITE); g.setFont(light.deriveFont(46f)); g.drawString(s[3], 20, 158);
      g.dispose();
      ImageIO.write(img, "png", new File(a[1], s[0] + ".png"));
    }
  }
}
