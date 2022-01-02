import gspread
import pandas as pd
from oauth2client.service_account import ServiceAccountCredentials

# Lists all spreadsheets that exist, might be useful later
# gc.openall()

gc = gspread.service_account('./private/credentials.json')

# get the instance of the Spreadsheet
sheet = gc.open('NGYA Timesheet')

# get the first sheet of the Spreadsheet
sheet_instance = sheet.get_worksheet(0)

