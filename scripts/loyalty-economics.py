"""Deterministic scenario model; no invented cost or uplift assumptions."""
from decimal import Decimal, ROUND_FLOOR
import csv
import sys

AOV = Decimal('150')
RUPEES_PER_COIN = Decimal('10')
LADDER = ((30,5,149),(75,15,249),(150,35,399),(300,75,699))
FACE = max(Decimal(value)/coins for coins,value,_ in LADDER)
REDEMPTION = (25,50,80,100)

writer = csv.writer(sys.stdout)
writer.writerow(('coins','discount','configured_minimum','effective_minimum_at_10_percent','required_spend','effective_cost_percent'))
for coins,value,minimum in LADDER:
    writer.writerow((coins,value,minimum,max(Decimal(minimum),Decimal(value)/Decimal('.10')),coins*RUPEES_PER_COIN,round(Decimal(value)/(coins*RUPEES_PER_COIN)*100,4)))
writer.writerow(())
writer.writerow(('customers','monthly_frequency','monthly_orders','monthly_product_gmv','annual_product_gmv','monthly_coins','maximum_monthly_face_liability','liability_25','liability_50','liability_80','liability_100','cost_percent_at_100_percent','incremental_contribution_required_at_100_percent'))
for customers in (1000,10000,50000,100000):
    for frequency in map(Decimal,('1','1.5','2','2.5','3')):
        orders=customers*frequency
        sales=orders*AOV
        coins=orders*(AOV/RUPEES_PER_COIN).quantize(Decimal('1'),rounding=ROUND_FLOOR)
        liability=coins*FACE
        writer.writerow((customers,frequency,orders,sales,sales*12,coins,liability,*(liability*Decimal(percent)/100 for percent in REDEMPTION),liability/sales*100,liability))
