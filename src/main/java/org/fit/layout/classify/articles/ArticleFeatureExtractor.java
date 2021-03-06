/**
 * FeatureAnalyzer.java
 *
 * Created on 6.5.2011, 14:48:51 by burgetr
 */
package org.fit.layout.classify.articles;

import java.awt.Color;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.fit.layout.classify.BackgroundColorAnalyzer;
import org.fit.layout.classify.ColorAnalyzer;
import org.fit.layout.classify.DefaultFeatureExtractor;
import org.fit.layout.model.Area;
import org.fit.layout.model.Box;
import org.fit.layout.model.Rectangular;
import org.fit.layout.model.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;


/**
 * A feature extractor for generic article processing.
 * 
 * @author burgetr
 */
public class ArticleFeatureExtractor extends DefaultFeatureExtractor
{
    private static Logger log = LoggerFactory.getLogger(ArticleFeatureExtractor.class);
    
    /** Minimal difference in the markedness that should be interpreted as a difference between the meaning of the areas. */
    public static final double MIN_MARKEDNESS_DIFFERENCE = 0.5; //0.5 is the difference between the whole area in italics and not in italics
    
    public static final double[] DEFAULT_WEIGHTS = {1000.0, 2.0, 0.5, 5.0, 0.0, 1.0, 0.5, 100.0}; 
    
    /** Maximal difference between left and right margin to consider the area to be centered (percentage of the parent area width) */
    public static final double CENTERING_THRESHOLD = 0.1;
    
    //weights
    private static final int WFSZ = 0; 
    private static final int WFWT = 1;
    private static final int WFST = 2; 
    private static final int WIND = 3;
    private static final int WCON = 4;
    private static final int WCEN = 5;
    private static final int WCP = 6;
    private static final int WBCP = 7;
    
    private double[] weights;
    
    private Area root;
    private float avgfont;
    private ColorAnalyzer ca;
    private BackgroundColorAnalyzer bca;
    
    public ArticleFeatureExtractor()
    {
        weights = DEFAULT_WEIGHTS;
    }
    
    @Override
    public void setTree(Area rootNode)
    {
        root = rootNode;
        avgfont = root.getFontSize();
        ca = new ColorAnalyzer(root);
        bca = new BackgroundColorAnalyzer(root);
    }
    
    @Override
    public Area getTreeRoot()
    {
        return root;
    }

    @Override
    public Instances createEmptyDataset()
    {
        try {
            return loadArffDatasetResource("articles_header.arff");
        } catch (Exception e) {
            log.error("Couldn't create empty dataset: " + e.getMessage());
            return null;
        }
    }

    @Override
    public Instance getAreaFeatures(Area node, Instances dataset)
    {
        FeatureVector f = getFeatureVector(node);
        
        Instance inst = new DenseInstance(30);
        inst.setDataset(dataset);
        int i = 0;
        inst.setValue(i++, 0.0); //id
        inst.setValue(i++, 0.0); //class
        inst.setValue(i++, f.getFontSize() * 100);
        inst.setValue(i++, f.getWeight());
        inst.setValue(i++, f.getStyle());
        inst.setValue(i++, f.isReplaced()?1:0);
        inst.setValue(i++, f.getAabove());
        inst.setValue(i++, f.getAbelow());
        inst.setValue(i++, f.getAleft());
        inst.setValue(i++, f.getAright());
        inst.setValue(i++, f.getNlines());
        inst.setValue(i++, 1); //TODO count columns
        inst.setValue(i++, f.getDepth());
        inst.setValue(i++, f.getTlength());
        inst.setValue(i++, f.getPdigits());
        inst.setValue(i++, f.getPlower());
        inst.setValue(i++, f.getPupper());
        inst.setValue(i++, f.getPspaces());
        inst.setValue(i++, f.getPpunct());
        inst.setValue(i++, f.getRelx());
        inst.setValue(i++, f.getRely());
        inst.setValue(i++, f.getTlum());
        inst.setValue(i++, f.getBglum());
        inst.setValue(i++, f.getContrast());
        inst.setValue(i++, f.getMarkedness());
        inst.setValue(i++, f.getCperc());
        /*Set<Tag> tags = getAllTags(node);
        inst.setValue(i++, tags.contains(tDate.getTag())?"true":"false");
        inst.setValue(i++, tags.contains(tTime.getTag())?"true":"false");
        inst.setValue(i++, tags.contains(tPersons.getTag())?"true":"false");
        inst.setValue(i++, tags.contains(tTitle.getTag())?"true":"false");*/
        
        return inst;
    }
    
    public void setWeights(double[] weights)
    {
        this.weights = weights;
    }
    
    public double[] getWeights()
    {
        return weights;
    }
    
    /**
     * Computes the markedness of the area. The markedness generally describes the visual importance of the area based on different criteria.
     * @return the computed expressiveness
     */
    public double getMarkedness(Area node)
    {
        double fsz = node.getFontSize() / avgfont; //use relative font size, 0 is the normal font
        double fwt = node.getFontWeight();
        double fst = node.getFontStyle();
        double ind = getIndentation(node);
        double cen = isCentered(node) ? 1.0 : 0.0;
        double contrast = getContrast(node);
        double cp = 1.0 - ca.getColorPercentage(node);
        double bcp = bca.getColorPercentage(node);
        bcp = (bcp < 0.0) ? 0.0 : (1.0 - bcp);
        
        //weighting
        double exp = weights[WFSZ] * fsz 
                      + weights[WFWT] * fwt 
                      + weights[WFST] * fst 
                      + weights[WIND] * ind
                      + weights[WCON] * contrast
                      + weights[WCEN] * cen
                      + weights[WCP] * cp
                      + weights[WBCP] * bcp;
        
        return exp;
    }
    
    //========================================================================================================
    
    public FeatureVector getFeatureVector(Area node)
    {
        FeatureVector ret = new FeatureVector();
        String text = node.getText();
        int plen = text.length();
        if (plen == 0) plen = 1; //kvuli deleni nulou
        
        ret.setFontSize(node.getFontSize() / avgfont);
        ret.setWeight(node.getFontWeight());
        ret.setStyle(node.getFontStyle());
        ret.setReplaced(node.isReplaced());
        ret.setAabove(countAreasAbove(node));
        ret.setAbelow(countAreasBelow(node));
        ret.setAleft(countAreasLeft(node));
        ret.setAright(countAreasRight(node));
        ret.setNlines(getLineCount(node));
        ret.setDepth(node.getDepth() + 1); //+2: annotator counts the boxes and their areas as well
        ret.setTlength(text.length());
        ret.setPdigits(countChars(text, Character.DECIMAL_DIGIT_NUMBER) / (double) plen);
        ret.setPlower(countChars(text, Character.LOWERCASE_LETTER) / (double) plen);
        ret.setPupper(countChars(text, Character.UPPERCASE_LETTER) / (double) plen);
        ret.setPspaces(countChars(text, Character.SPACE_SEPARATOR) / (double) plen);
        ret.setPpunct(countCharsPunct(text) / (double) plen);
        ret.setRelx(getRelX(node));
        ret.setRely(getRelY(node));
        ret.setTlum(getAverageTextLuminosity(node));
        ret.setBglum(getBackgroundLuminosity(node));
        ret.setContrast(getContrast(node));
        ret.setCperc(ca.getColorPercentage(node));
        ret.setBcperc(bca.getColorPercentage(node));
        ret.setMarkedness(getMarkedness(node));
        Tag t = node.getMostSupportedTag();
        ret.setTagLevel(t == null ? -1 : t.getLevel());
        
        //TODO ostatni vlastnosti obdobne
        return ret;
    }
    
    /**
     * Checks whether the area is horizontally centered within its parent area
     * @return <code>true</code> if the area is centered
     */
    public boolean isCentered(Area area)
    {
        return isCentered(area, true, true) == 1;
    }
    
    /**
     * Tries to guess whether the area is horizontally centered within its parent area 
     * @param askBefore may we compare the alignment with the preceding siblings?
     * @param askAfter may we compare the alignment with the following siblings?
     * @return 0 when certailny not centered, 1 when certainly centered, 2 when not sure (nothing to compare with and no margins around)
     */
    private int isCentered(Area area, boolean askBefore, boolean askAfter)
    {
        Area parent = area.getParentArea();
        if (parent != null)
        {
            int left = area.getX1() - parent.getX1();
            int right = parent.getX2() - area.getX2();
            int limit = (int) (((left + right) / 2.0) * CENTERING_THRESHOLD);
            if (limit == 0) limit = 1; //we always allow +-1px
            //System.out.println(this + " left=" + left + " right=" + right + " limit=" + limit);
            boolean middle = Math.abs(left - right) <= limit; //first guess - check if it is placed in the middle
            boolean fullwidth = left == 0 && right == 0; //centered because of full width
            
            if (!middle && !fullwidth) //not full width and certainly not in the middle
            {
                return 0; 
            }
            else //may be centered - check the alignment
            {
                //compare the alignent with the previous and/or the next child
                Area prev = null;
                Area next = null;
                int pc = 2; //previous centered?
                int nc = 2; //next cenrered?
                if (askBefore || askAfter)
                {
                    if (askBefore)
                    {
                        prev = area.getPreviousSibling();
                        while (prev != null && (pc = isCentered(prev, true, false)) == 2)
                            prev = prev.getPreviousSibling();
                    }
                    if (askAfter)
                    {
                        next = area.getNextSibling();
                        while (next != null && (nc = isCentered(next, false, true)) == 2)
                            next = next.getNextSibling();
                    }
                }
                
                if (pc != 2 || nc != 2) //we have something for comparison
                {
                    if (fullwidth) //cannot guess, compare with others
                    {
                        if (pc != 0 && nc != 0) //something around is centered - probably centered
                            return 1;
                        else
                            return 0;
                    }
                    else //probably centered, if it is not left- or right-aligned with something around
                    {
                        if (prev != null && lrAligned(area, prev) == 1 ||
                            next != null && lrAligned(area, next) == 1)
                            return 0; //aligned, not centered
                        else
                            return 1; //probably centered
                    }
                }
                else //nothing to compare, just guess
                {
                    if (fullwidth)
                        return 2; //cannot guess from anything
                    else
                        return (middle ? 1 : 0); //nothing to compare with - guess from the position
                }
            }
        }
        else
            return 2; //no parent - we don't know
    }
    
    /**
     * Checks if the areas are left- or right-aligned.
     * @return 0 if not, 1 if yes, 2 if both left and right
     */
    private int lrAligned(Area a1, Area a2)
    {
        if (a1.getX1() == a2.getX1())
            return (a1.getX2() == a2.getX2()) ? 2 : 1;
        else if (a1.getX2() == a2.getX2())
            return 1;
        else
            return 0;
    }

    /**
     * Computes the indentation metric.
     * @return the indentation metric (0..1) where 1 is for the non-indented areas, 0 for the most indented areas.
     */
    public double getIndentation(Area node)
    {
        final double max_levels = 3;
        
        if (node.getTopology().getPreviousOnLine() != null)
            return getIndentation(node.getTopology().getPreviousOnLine()); //use the indentation of the first one on the line
        else
        {
            double ind = max_levels;
            if (!isCentered(node) && node.getParentArea() != null)
                ind = ind - (node.getTopology().getPosition().getX1() - node.getParentArea().getTopology().getMinIndent());
            if (ind < 0) ind = 0;
            return ind / max_levels;
        }
    }
    
    /**
     * Counts the number of sub-areas in the specified region of the area
     * @param a the area to be examined
     * @param r the grid region of the area to be examined
     * @return the number of visual areas in the specified area of the grid
     */
    private int countAreas(Area a, Rectangular r)
    {
        int ret = 0;
        
        for (int i = 0; i < a.getChildCount(); i++)
        {
            Area n = a.getChildArea(i);
            if (n.getTopology().getPosition().intersects(r))
                ret++;
        }
        return ret;
    }
    
    private int countAreasAbove(Area a)
    {
        Rectangular gp = a.getTopology().getPosition();
        Area parent = a.getParentArea();
        if (parent != null)
        {
            Rectangular r = new Rectangular(gp.getX1(), 0, gp.getX2(), gp.getY1() - 1);
            return countAreas(parent, r);
        }
        else
            return 0;
    }

    private int countAreasBelow(Area a)
    {
        Rectangular gp = a.getTopology().getPosition();
        Area parent = a.getParentArea();
        if (parent != null)
        {
            Rectangular r = new Rectangular(gp.getX1(), gp.getY2()+1, gp.getX2(), Integer.MAX_VALUE);
            return countAreas(parent, r);
        }
        else
            return 0;
    }

    private int countAreasLeft(Area a)
    {
        Rectangular gp = a.getTopology().getPosition();
        Area parent = a.getParentArea();
        if (parent != null)
        {
            Rectangular r = new Rectangular(0, gp.getY1(), gp.getX1() - 1, gp.getY2());
            return countAreas(parent, r);
        }
        else
            return 0;
    }

    private int countAreasRight(Area a)
    {
        Rectangular gp = a.getTopology().getPosition();
        Area parent = a.getParentArea();
        if (parent != null)
        {
            Rectangular r = new Rectangular(gp.getX2()+1, gp.getY1(), Integer.MAX_VALUE, gp.getY2());
            return countAreas(parent, r);
        }
        else
            return 0;
    }

    private int countChars(String s, int type)
    {
        int ret = 0;
        for (int i = 0; i < s.length(); i++)
            if (Character.getType(s.charAt(i)) == type)
                    ret++;
        return ret;
    }

    private int countCharsPunct(String s)
    {
        int ret = 0;
        for (int i = 0; i < s.length(); i++)
        {
            char ch = s.charAt(i);
            if (ch == ',' || ch == '.' || ch == ';' || ch == ':')
                    ret++;
        }
        return ret;
    }
    
    private double getAverageTextLuminosity(Area a)
    {
        double sum = 0;
        int cnt = 0;
        
        if (!a.getBoxes().isEmpty()) //has some content
        {
            int l = a.getText().length();
            sum += getAverageBoxColorLuminosity(a) * l;
            cnt += l;
        }
        
        for (int i = 0; i < a.getChildCount(); i++)
        {
            int l = a.getChildArea(i).getText().length();
            sum += getAverageTextLuminosity(a.getChildArea(i)) * l;
            cnt += l;
        }
        
        if (cnt > 0)
            return sum / cnt;
        else
            return 0;
    }
    
    public double getAverageBoxColorLuminosity(Area area)
    {
        if (area.getBoxes().isEmpty())
            return 0;
        else
        {
            double sum = 0;
            int len = 0;
            for (Box box : area.getBoxes())
            {
                int l = box.getText().length(); 
                sum += colorLuminosity(box.getColor()) * l;
                len += l;
            }
            return sum / len;
        }
    }
    
    private double getBackgroundLuminosity(Area a)
    {
        Color bg = a.getEffectiveBackgroundColor();
        if (bg != null)
            return ArticleFeatureExtractor.colorLuminosity(bg);
        else
            return 0;
    }
    
    private double getContrast(Area a)
    {
        double bb = getBackgroundLuminosity(a);
        double tb = getAverageTextLuminosity(a);
        double lum;
        if (bb > tb)
            lum = (bb + 0.05) / (tb + 0.05);
        else
            lum = (tb + 0.05) / (bb + 0.05);
        return lum;
    }
    
    public static double colorLuminosity(Color c)
    {
        double lr, lg, lb;
        if (c == null)
        {
            lr = lg = lb = 255;
        }
        else
        {
            lr = Math.pow(c.getRed() / 255.0, 2.2);
            lg = Math.pow(c.getGreen() / 255.0, 2.2);
            lb = Math.pow(c.getBlue() / 255.0, 2.2);
        }
        return lr * 0.2126 +  lg * 0.7152 + lb * 0.0722;
    }

    private double getRelX(Area a)
    {
        int objx1 = a.getX1();
        if (objx1 < 0) objx1 = 0;
        int objx2 = a.getX2();
        if (objx2 < 0) objx2 = 0;
        
        int topx1 = root.getX1();
        if (topx1 < 0) topx1 = 0;
        int topx2 = root.getX2();
        if (topx2 < 0) topx2 = 0;
        
        double midw = (objx2 - objx1) / 2.0;
        double topx = topx1 + midw; //zacatek oblasti, kde lze objektem posunovat
        double midx = (objx1 + objx2) / 2.0 - topx; //stred objektu v ramci teto oblasti
        double topw = (topx2 - topx1) - (objx2 - objx1); //sirka, kam lze stredem posunovat
        return midx / topw;
    }

    public double getRelY(Area a)
    {
        int objy1 = a.getY1();
        if (objy1 < 0) objy1 = 0;
        int objy2 = a.getY2();
        if (objy2 < 0) objy2 = 0;
        
        int topy1 = root.getY1();
        if (topy1 < 0) topy1 = 0;
        int topy2 = root.getY2();
        if (topy2 < 0) topy2 = 0;
        
        double midh = (objy2 - objy1) / 2.0;
        double topy = topy1 + midh; //zacatek oblasti, kde lze objektem posunovat
        double midy = (objy1 + objy2) / 2.0 - topy; //stred objektu v ramci teto oblasti
        double toph = (topy2 - topy1) - (objy2 - objy1); //sirka, kam lze stredem posunovat
        return midy / toph;
    }
    
    public int getLineCount(Area a)
    {
        final int LINE_THRESHOLD = 5; //minimal distance between lines in pixels
        
        List<Box> leaves = a.getAllBoxes();
        Collections.sort(leaves, new AbsoluteYPositionComparator());
        int lines = 0;
        int lastpos = -10;
        for (Box leaf : leaves)
        {
            int pos = leaf.getBounds().getY1();
            if (pos - lastpos > LINE_THRESHOLD)
            {
                lines++;
                lastpos = pos;
            }
        }
        return lines;
    }
    
    /**
     * Obtains all the tags assigned to this area and its child areas (not all descendant areas).
     * @return a set of tags
     */
    protected Set<Tag> getAllTags(Area area)
    {
        Set<Tag> ret = new HashSet<Tag>(area.getTags().keySet());
        for (int i = 0; i < area.getChildCount(); i++)
            ret.addAll(area.getChildArea(i).getTags().keySet());
        return ret;
    }
    
    //========================================================================================================
    
    /**
     * Updates the weights according to the used style of presentation based on statistical tag analysis.
     * @param root
     */
    /*public void updateWeights(Area root, SearchTree stree)
    {
    }*/
    
    
    //============================================================================================
    
    class AbsoluteXPositionComparator implements Comparator<Box>
    {
        @Override
        public int compare(Box o1, Box o2)
        {
            return o1.getBounds().getX1() - o2.getBounds().getX1(); 
        }
    }
    
    class AbsoluteYPositionComparator implements Comparator<Box>
    {
        @Override
        public int compare(Box o1, Box o2)
        {
            return o1.getBounds().getY1() - o2.getBounds().getY1(); 
        }
    }
    
}
