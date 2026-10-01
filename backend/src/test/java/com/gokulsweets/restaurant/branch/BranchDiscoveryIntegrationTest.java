package com.gokulsweets.restaurant.branch;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest @Transactional
class BranchDiscoveryIntegrationTest {
 @Autowired JdbcTemplate jdbc;
 @Autowired BranchOfferingsService offerings;
 @Autowired BranchDiscoveryService discovery;
 @MockitoBean StaffAuthorizationService staff;
 String key(){return UUID.randomUUID().toString();}
 long branch(){return jdbc.queryForObject("INSERT INTO branches(code,name) VALUES (?,?) RETURNING id",Long.class,key(),"Discovery branch");}
 long slot(long branch){return jdbc.queryForObject("INSERT INTO pickup_slots(branch_id,slot_date,start_time,end_time,capacity) VALUES (?,CURRENT_DATE,'10:00','11:00',50) RETURNING id",Long.class,branch);}
 long product(long branch,String name,boolean available,boolean occasion){
  long cat=jdbc.queryForObject("INSERT INTO categories(code,name) VALUES (?,?) RETURNING id",Long.class,key(),"Discovery category");
  long tax=jdbc.queryForObject("INSERT INTO tax_categories(code,name) VALUES (?,?) RETURNING id",Long.class,key(),"Discovery tax");
  long p=jdbc.queryForObject("INSERT INTO products(code,category_id,tax_category_id,name,base_price) VALUES (?,?,?,?,100) RETURNING id",Long.class,key(),cat,tax,name);
  jdbc.update("INSERT INTO branch_products(branch_id,product_id,available,occasion_only) VALUES (?,?,?,?)",branch,p,available,occasion);return p;
 }
 void review(long branch,long slot,long p,int overall,int item,String status,String comment,String orderStatus){
  long order=jdbc.queryForObject("INSERT INTO orders(order_number,branch_id,pickup_slot_id,customer_name,customer_phone,pickup_type,order_status,reservation_expires_at) VALUES (?,?,?,'Private customer','9876543210','NORMAL',?,CURRENT_TIMESTAMP) RETURNING id",Long.class,key(),branch,slot,orderStatus);
  long review=jdbc.queryForObject("INSERT INTO reviews(order_id,branch_id,overall_rating,comment,review_status,created_at,updated_at) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) RETURNING id",Long.class,order,branch,overall,comment,status);
  jdbc.update("INSERT INTO review_items(review_id,product_id,product_name,rating,created_at,updated_at) VALUES (?,?,'Historical name',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",review,p,item);
 }
 @Test void offeringsAreBranchScopedDraftPrivateVersionedAndCanBeRemoved(){
  long id=branch(),other=branch();var first=offerings.save(id,new BranchOfferingsService.Input(List.of(new BranchOfferingsService.Offering("A real speciality","Configured by the branch"))),0);
  assertThat(offerings.published(id)).isEmpty();assertThat(offerings.published(other)).isEmpty();
  assertThatThrownBy(()->offerings.publish(id,0)).isInstanceOf(ResponseStatusException.class);
  var live=offerings.publish(id,first.version());assertThat(offerings.published(id).getFirst().title()).isEqualTo("A real speciality");
  var empty=offerings.save(id,new BranchOfferingsService.Input(List.of()),live.version());assertThat(offerings.published(id)).hasSize(1);offerings.publish(id,empty.version());assertThat(offerings.published(id)).isEmpty();
  verify(staff,atLeastOnce()).requirePermission(PermissionName.BRANCH_MANAGE);
  doThrow(new AccessDeniedException("Other branch")).when(staff).requireBranchAccess(other);
  assertThatThrownBy(()->offerings.admin(other)).isInstanceOf(AccessDeniedException.class);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM branch_offering_settings WHERE branch_id=?",Long.class,other)).isZero();
 }
 @Test void publishedCompletedExperienceAndItemRatingsNeverMixBranchesOrHiddenReviews(){
  long b=branch(),s=slot(b),p=product(b,"Top rated sweet",true,false);
  review(b,s,p,5,4,"PUBLISHED","Fresh and carefully packed for our pickup.","PICKED_UP");
  review(b,s,p,3,2,"PUBLISHED","The overall pickup experience was reasonable.","PICKED_UP");
  review(b,s,p,1,1,"HIDDEN","This hidden review must never appear publicly.","PICKED_UP");
  review(b,s,p,1,1,"PUBLISHED","An unfinished order must not affect public ratings.","CONFIRMED");
  long other=branch();review(other,slot(other),p,1,1,"PUBLISHED","Another branch must not affect these ratings.","PICKED_UP");
  var result=discovery.get(b);assertThat(result.overallExperience().average()).isEqualTo(4);assertThat(result.overallExperience().count()).isEqualTo(2);
  assertThat(result.topRatedItems()).hasSize(1);var top=result.topRatedItems().getFirst();assertThat(top.name()).isEqualTo("Top rated sweet");assertThat(top.average()).isEqualTo(3);assertThat(top.count()).isEqualTo(2);
  assertThat(top.review().comment()).doesNotContain("hidden","unfinished","Another branch");
  jdbc.update("UPDATE branch_products SET available=false WHERE branch_id=? AND product_id=?",b,p);assertThat(discovery.get(b).topRatedItems()).isEmpty();assertThat(discovery.get(b).overallExperience().count()).isEqualTo(2);
 }
 @Test void emptyBranchesAndOccasionOnlyItemsDoNotInventRatings(){
  long b=branch();assertThat(discovery.get(b).overallExperience().count()).isZero();assertThat(discovery.get(b).topRatedItems()).isEmpty();
  long p=product(b,"Occasion exclusive",true,true);review(b,slot(b),p,5,5,"PUBLISHED","A thoughtful and meaningful review of the order.","PICKED_UP");assertThat(discovery.get(b).topRatedItems()).isEmpty();
  jdbc.update("UPDATE branches SET active=false WHERE id=?",b);assertThatThrownBy(()->discovery.get(b)).isInstanceOf(ResponseStatusException.class);
 }
 @Test void shortOrContactBearingCommentsAreNotPublishedAsQuotes(){
  assertThat(BranchDiscoveryService.publicComment("Nice")).isNull();assertThat(BranchDiscoveryService.publicComment("Contact me at name@example.com for more information")).isNull();assertThat(BranchDiscoveryService.publicComment("My phone is 9876543210 and this was good" )).isNull();assertThat(BranchDiscoveryService.publicComment(" Fresh sweets\nwith careful packing and good service. ")).isEqualTo("Fresh sweets with careful packing and good service.");
 }
}
