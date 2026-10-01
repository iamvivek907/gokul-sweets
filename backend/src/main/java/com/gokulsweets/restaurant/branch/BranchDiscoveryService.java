package com.gokulsweets.restaurant.branch;
import com.gokulsweets.restaurant.menu.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.regex.Pattern;

@Service @RequiredArgsConstructor
public class BranchDiscoveryService {
 private final BranchRepository branches;
 private final BranchOfferingsService offerings;
 private final JdbcTemplate jdbc;
 private final MenuService menu;
 public record Rating(double average,long count) {}
 public record Excerpt(String comment,int overallRating) {}
 public record RatedItem(long productId,String name,String imageUrl,long categoryId,double average,long count,Excerpt review) {}
 public record Discovery(List<BranchOfferingsService.Offering> offerings,Rating overallExperience,List<RatedItem> topRatedItems) {}
 private static final Pattern PRIVATE_CONTACT=Pattern.compile("(?i)[\\w.+-]+@[\\w.-]+\\.[a-z]{2,}|(?:\\+?\\d[\\d ()-]{7,}\\d)");
 static String publicComment(String comment) {
  if(comment==null)return null;
  String clean=comment.replaceAll("\\s+"," ").trim();
  return clean.length()<20 || PRIVATE_CONTACT.matcher(clean).find()?null:clean;
 }
 private static double round(double value){return BigDecimal.valueOf(value).setScale(1,RoundingMode.HALF_UP).doubleValue();}
 @Transactional(readOnly=true)
 public Discovery get(long id) {
  if(branches.findByIdAndActiveTrue(id).isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Branch not found.");
  var rating=jdbc.queryForObject("""
   SELECT COALESCE(AVG(r.overall_rating),0),COUNT(*) FROM reviews r JOIN orders o ON o.id=r.order_id
   WHERE r.branch_id=? AND r.review_status='PUBLISHED' AND o.order_status IN ('PICKED_UP','DELIVERED')
   """,(rs,n)->new Rating(round(rs.getDouble(1)),rs.getLong(2)),id);
  var available=menu.getMenu(id).stream().flatMap(c->c.products().stream()).filter(p->p.available()).collect(java.util.stream.Collectors.toMap(p->p.id(),p->p));
  var ranks=jdbc.query("""
   SELECT ri.product_id,AVG(ri.rating),COUNT(*) FROM review_items ri JOIN reviews r ON r.id=ri.review_id
   JOIN orders o ON o.id=r.order_id JOIN branch_products bp ON bp.product_id=ri.product_id AND bp.branch_id=r.branch_id
   JOIN products p ON p.id=ri.product_id
   WHERE r.branch_id=? AND r.review_status='PUBLISHED' AND o.order_status IN ('PICKED_UP','DELIVERED')
   AND bp.available AND NOT bp.occasion_only AND p.active
   GROUP BY ri.product_id ORDER BY AVG(ri.rating) DESC,COUNT(*) DESC,ri.product_id LIMIT 100
   """,(rs,n)->new RatedItem(rs.getLong(1),"",null,0,round(rs.getDouble(2)),rs.getLong(3),null),id);
  var top=ranks.stream().filter(r->available.containsKey(r.productId())).limit(3).map(r->{
   var product=available.get(r.productId());
   var excerpts=jdbc.query("""
    SELECT r.comment,r.overall_rating FROM reviews r JOIN review_items ri ON ri.review_id=r.id JOIN orders o ON o.id=r.order_id
    WHERE r.branch_id=? AND ri.product_id=? AND r.review_status='PUBLISHED' AND o.order_status IN ('PICKED_UP','DELIVERED')
    AND LENGTH(TRIM(r.comment))>=20 ORDER BY r.created_at DESC,r.id DESC LIMIT 20
    """,(rs,n)->new Excerpt(rs.getString(1),rs.getInt(2)),id,r.productId());
   var excerpt=excerpts.stream().filter(e->publicComment(e.comment())!=null).findFirst().map(e->new Excerpt(publicComment(e.comment()),e.overallRating())).orElse(null);
   return new RatedItem(r.productId(),product.name(),product.imageUrl(),product.categoryId(),r.average(),r.count(),excerpt);
  }).toList();
  return new Discovery(offerings.published(id),rating,top);
 }
}
