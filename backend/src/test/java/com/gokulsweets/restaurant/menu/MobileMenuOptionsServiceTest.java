package com.gokulsweets.restaurant.menu;

import com.gokulsweets.restaurant.branch.BranchRepository;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MobileMenuOptionsServiceTest {
 @Test void fortyGroupsUseOneChoicesQuery(){
  var jdbc=mock(JdbcTemplate.class);
  var service=new MobileMenuOptionsService(jdbc,mock(BranchRepository.class),mock(StaffAuthorizationService.class));
  List<MobileMenuOptionsService.Group> groups=new ArrayList<>();
  for(int i=0;i<40;i++)groups.add(new MobileMenuOptionsService.Group("group-"+i,"Meal "+i,List.of()));
  when(jdbc.queryForList("SELECT version FROM mobile_menu_config WHERE branch_id=?",Long.class,1L)).thenReturn(List.of(3L));
  when(jdbc.query(eq("SELECT group_key,title FROM mobile_menu_groups WHERE branch_id=? ORDER BY position"),org.mockito.ArgumentMatchers.<RowMapper<MobileMenuOptionsService.Group>>any(),eq(1L))).thenReturn(groups);
  doAnswer(call->{
   RowCallbackHandler callback=call.getArgument(1);
   for(int i=39;i>=0;i--)for(int portion=0;portion<2;portion++){
    var rs=mock(ResultSet.class);when(rs.getString(1)).thenReturn("group-"+i);when(rs.getLong(2)).thenReturn((long)i*2+portion);when(rs.getString(3)).thenReturn(portion==0?"Half":"Full");callback.processRow(rs);
   }
   return null;
  }).when(jdbc).query(eq("SELECT group_key,product_id,label FROM mobile_menu_choices WHERE branch_id=? ORDER BY group_key,position"),any(RowCallbackHandler.class),eq(1L));
  var result=service.adminRead(1L);
  assertThat(result.version()).isEqualTo(3L);assertThat(result.groups()).hasSize(40);
  for(int i=0;i<40;i++){
   assertThat(result.groups().get(i).key()).isEqualTo("group-"+i);
   assertThat(result.groups().get(i).choices()).containsExactly(new MobileMenuOptionsService.Choice((long)i*2,"Half"),new MobileMenuOptionsService.Choice((long)i*2+1,"Full"));
  }
  verify(jdbc,times(1)).query(anyString(),any(RowCallbackHandler.class),eq(1L));
 }
}
